import { camera } from '@kit.CameraKit';
import { common, abilityAccessCtrl, Permissions } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { RtspServer } from './RtspServer';
import { RtmpPushManager, RtmpPushConfig, parseRtmpUrl, RtmpPushStatus } from './RtmpPushManager';
import { PHONE_RTSP_PORT } from './NetworkUtils';
import { rstr, rstrNum } from './I18nUtils';
import { createEncoder, startEncoder, stopEncoder, releaseEncoder, EncodedFrame } from 'librtspencoder.so';

const TAG = 'CameraStreamManager';

export interface StreamStatus {
  isStreaming: boolean;
  isPreviewReady: boolean;
  statusText: string;
  clientCount: number;
}

export class CameraStreamManager {
  private context: common.Context;
  private rtspServer: RtspServer;
  private rtmpPushManager: RtmpPushManager | null = null;
  private streamMode: 'rtsp' | 'rtmp' = 'rtsp';
  private rtmpConfig: RtmpPushConfig | null = null;
  private rtmpStatusText: string = '';
  private cameraManager: camera.CameraManager | null = null;
  private cameraInput: camera.CameraInput | null = null;
  private previewOutput: camera.PreviewOutput | null = null;
  private videoOutput: camera.VideoOutput | null = null;
  private session: camera.VideoSession | null = null;
  private encoderSurfaceId: string = '';
  private previewSurfaceId: string = '';
  private useFrontCamera: boolean = false;
  private streaming: boolean = false;
  private previewReady: boolean = false;
  private statusCallback: ((status: StreamStatus) => void) | null = null;

  private width: number = 640;
  private height: number = 480;
  private bitrate: number = 1_200_000;
  private framerate: number = 30;

  constructor(context: common.Context) {
    this.context = context;
    this.rtspServer = new RtspServer(PHONE_RTSP_PORT);
    this.rtspServer.setOnClientCountChange((count: number) => {
      this.notifyStatus();
    });
  }

  setStatusCallback(cb: (status: StreamStatus) => void): void {
    this.statusCallback = cb;
  }

  setStreamMode(mode: 'rtsp' | 'rtmp'): void {
    if (this.streaming) return;
    this.streamMode = mode;
    console.info(`${TAG} stream mode set to ${mode}`);
  }

  setRtmpConfig(url: string, streamKey: string, username: string, password: string): void {
    const parsed = parseRtmpUrl(url);
    this.rtmpConfig = {
      host: parsed.host,
      port: parsed.port,
      app: parsed.app,
      streamKey: streamKey,
      username: username,
      password: password,
    };
    console.info(`${TAG} RTMP config: host=${parsed.host}, port=${parsed.port}, app=${parsed.app}, key=${streamKey}`);
  }

  private notifyStatus(): void {
    if (this.statusCallback) {
      const clientCount = this.streamMode === 'rtsp' ? this.rtspServer.getClientCount() : 0;
      this.statusCallback({
        isStreaming: this.streaming,
        isPreviewReady: this.previewReady,
        statusText: this.getStatusText(),
        clientCount: clientCount,
      });
    }
  }

  private getStatusText(): string {
    if (this.streaming) {
      if (this.streamMode === 'rtmp') {
        if (this.rtmpPushManager && this.rtmpPushManager.isPublishing()) {
          return rstr(this.context, 'cam_status_rtmp_streaming');
        }
        return this.rtmpStatusText || rstr(this.context, 'cam_status_rtmp_connecting');
      }
      const count = this.rtspServer.getClientCount();
      if (count > 0) {
        return rstrNum(this.context, 'cam_status_streaming_clients', count);
      }
      return rstr(this.context, 'cam_status_streaming_waiting');
    }
    if (this.previewReady) {
      return rstr(this.context, 'cam_status_preview_ready');
    }
    return rstr(this.context, 'cam_status_not_started');
  }

  private async ensureCameraPermission(): Promise<boolean> {
    try {
      const atManager: abilityAccessCtrl.AtManager = abilityAccessCtrl.createAtManager();
      const uiContext = this.context as common.UIAbilityContext;
      const permissions: Array<Permissions> = ['ohos.permission.CAMERA'];
      console.info(`${TAG} ensureCameraPermission: requesting CAMERA permission...`);
      const result = await atManager.requestPermissionsFromUser(uiContext, permissions);
      console.info(`${TAG} ensureCameraPermission: request result authResults=${JSON.stringify(result.authResults)}`);
      return result.authResults.length > 0 && result.authResults[0] === 0;
    } catch (e) {
      const err = e as Error;
      console.error(`${TAG} ensureCameraPermission EXCEPTION: ${err.message}`);
      return false;
    }
  }

  async initPreview(previewSurfaceId: string): Promise<void> {
    this.previewSurfaceId = previewSurfaceId;
    console.info(`${TAG} initPreview BEGIN: surfaceId=${previewSurfaceId}`);
    try {
      console.info(`${TAG} initPreview: checking camera permission...`);
      const hasPermission = await this.ensureCameraPermission();
      if (!hasPermission) {
        console.error(`${TAG} initPreview: CAMERA permission denied`);
        this.statusCallback?.({
          isStreaming: false,
          isPreviewReady: false,
          statusText: rstr(this.context, 'cam_status_need_camera_permission'),
          clientCount: 0,
        });
        return;
      }
      console.info(`${TAG} initPreview: camera permission granted`);

      console.info(`${TAG} initPreview: getting CameraManager...`);
      this.cameraManager = camera.getCameraManager(this.context);
      console.info(`${TAG} initPreview: CameraManager obtained`);

      const cameras = this.cameraManager.getSupportedCameras();
      console.info(`${TAG} initPreview: found ${cameras.length} cameras`);
      if (cameras.length === 0) {
        console.error(`${TAG} initPreview: No cameras found`);
        this.statusCallback?.({
          isStreaming: false,
          isPreviewReady: false,
          statusText: rstr(this.context, 'cam_status_no_camera'),
          clientCount: 0,
        });
        return;
      }

      const cameraDevice = this.useFrontCamera ? cameras[0] : cameras[cameras.length - 1];
      console.info(`${TAG} initPreview: selected camera index=${this.useFrontCamera ? 0 : cameras.length - 1}, id=${cameraDevice.cameraId}`);

      const outputCapability = this.cameraManager.getSupportedOutputCapability(cameraDevice, camera.SceneMode.NORMAL_VIDEO);
      console.info(`${TAG} initPreview: previewProfiles count=${outputCapability.previewProfiles.length}, videoProfiles count=${outputCapability.videoProfiles.length}`);

      console.info(`${TAG} initPreview: creating CameraInput...`);
      this.cameraInput = this.cameraManager.createCameraInput(cameraDevice);
      console.info(`${TAG} initPreview: CameraInput created, opening...`);
      await this.cameraInput.open();
      console.info(`${TAG} initPreview: CameraInput opened`);

      const previewProfile = outputCapability.previewProfiles.find((p: camera.Profile) => {
        return p.size.width === this.width && p.size.height === this.height;
      }) ?? outputCapability.previewProfiles[0];
      console.info(`${TAG} initPreview: previewProfile size=${previewProfile.size.width}x${previewProfile.size.height}, format=${previewProfile.format}`);
      console.info(`${TAG} initPreview: creating PreviewOutput with surfaceId=${previewSurfaceId}...`);
      this.previewOutput = this.cameraManager.createPreviewOutput(previewProfile, previewSurfaceId);
      console.info(`${TAG} initPreview: PreviewOutput created`);

      console.info(`${TAG} initPreview: creating VideoSession...`);
      this.session = this.cameraManager.createSession(camera.SceneMode.NORMAL_VIDEO) as camera.VideoSession;
      console.info(`${TAG} initPreview: VideoSession created: ${this.session !== null}`);
      if (!this.session || !this.cameraInput || !this.previewOutput) {
        console.error(`${TAG} initPreview: Failed to create session/input/output: session=${this.session !== null}, input=${this.cameraInput !== null}, output=${this.previewOutput !== null}`);
        this.statusCallback?.({
          isStreaming: false,
          isPreviewReady: false,
          statusText: rstr(this.context, 'cam_status_session_failed'),
          clientCount: 0,
        });
        return;
      }
      console.info(`${TAG} initPreview: beginConfig...`);
      this.session.beginConfig();
      console.info(`${TAG} initPreview: addInput...`);
      this.session.addInput(this.cameraInput);
      console.info(`${TAG} initPreview: addOutput(preview)...`);
      this.session.addOutput(this.previewOutput);

      console.info(`${TAG} initPreview: commitConfig...`);
      await this.session.commitConfig();
      console.info(`${TAG} initPreview: commitConfig done, starting session...`);
      await this.session.start();
      console.info(`${TAG} initPreview: session started`);

      this.previewReady = true;
      this.notifyStatus();
      console.info(`${TAG} initPreview END: success`);
    } catch (e) {
      const err = e as Error;
      console.error(`${TAG} initPreview EXCEPTION: ${err.message}, stack=${err.stack}`);
      this.statusCallback?.({
        isStreaming: false,
        isPreviewReady: false,
        statusText: rstr(this.context, 'cam_status_preview_init_failed', err.message),
        clientCount: 0,
      });
    }
  }

  async startStream(): Promise<void> {
    if (this.streaming) return;
    console.info(`${TAG} startStream BEGIN`);
    try {
      console.info(`${TAG} startStream: creating encoder ${this.width}x${this.height} bitrate=${this.bitrate} fps=${this.framerate}...`);
      this.encoderSurfaceId = createEncoder(this.width, this.height, this.bitrate, this.framerate,
        (frame: EncodedFrame) => {
          this.onEncodedFrame(frame);
        });
      console.info(`${TAG} startStream: encoder surfaceId=${this.encoderSurfaceId}`);

      if (!this.encoderSurfaceId) {
        console.error(`${TAG} startStream: Failed to create encoder, surfaceId is empty`);
        this.notifyStatus();
        return;
      }

      if (this.streamMode === 'rtmp') {
        console.info(`${TAG} startStream: RTMP mode, connecting to server...`);
        if (!this.rtmpConfig) {
          console.error(`${TAG} startStream: RTMP config not set`);
          this.statusCallback?.({
            isStreaming: false,
            isPreviewReady: this.previewReady,
            statusText: rstr(this.context, 'cam_status_configure_rtmp'),
            clientCount: 0,
          });
          return;
        }
        this.rtmpPushManager = new RtmpPushManager();
        this.rtmpPushManager.setContext(this.context);
        this.rtmpPushManager.setOnStatusChange((status: RtmpPushStatus, message?: string) => {
          if (message) this.rtmpStatusText = message;
          else this.rtmpStatusText = '';
          this.notifyStatus();
        });
        try {
          await this.rtmpPushManager.connect(this.rtmpConfig);
          console.info(`${TAG} startStream: RTMP connect initiated`);
        } catch (connectErr) {
          console.error(`${TAG} startStream: RTMP connect failed: ${(connectErr as Error).message}`);
          this.streaming = false;
          this.rtmpPushManager = null;
          this.notifyStatus();
          return;
        }
      } else {
        console.info(`${TAG} startStream: starting RTSP server...`);
        await this.rtspServer.start();
        console.info(`${TAG} startStream: RTSP server started`);
      }

      if (this.cameraManager && this.session) {
        console.info(`${TAG} startStream: creating VideoOutput...`);
        const cameras = this.cameraManager.getSupportedCameras();
        const cameraDevice = this.useFrontCamera ? cameras[0] : cameras[cameras.length - 1];
        const outputCapability = this.cameraManager.getSupportedOutputCapability(cameraDevice, camera.SceneMode.NORMAL_VIDEO);

        const videoProfile = outputCapability.videoProfiles.find((p: camera.VideoProfile) => {
          return p.size.width === this.width && p.size.height === this.height;
        }) ?? outputCapability.videoProfiles[0];
        console.info(`${TAG} startStream: videoProfile size=${videoProfile.size.width}x${videoProfile.size.height}`);

        this.videoOutput = this.cameraManager.createVideoOutput(videoProfile, this.encoderSurfaceId);
        console.info(`${TAG} startStream: VideoOutput created`);

        console.info(`${TAG} startStream: stopping session before reconfig...`);
        try {
          await this.session.stop();
        } catch (stopErr) {
          console.warn(`${TAG} startStream: session.stop() failed (may already be stopped): ${(stopErr as Error).message}`);
        }
        console.info(`${TAG} startStream: session stopped, beginConfig...`);
        this.session.beginConfig();
        this.session.addOutput(this.videoOutput);
        await this.session.commitConfig();
        console.info(`${TAG} startStream: session reconfigured with video output, restarting...`);
        await this.session.start();
        console.info(`${TAG} startStream: session restarted`);

        this.videoOutput.start();
        console.info(`${TAG} startStream: videoOutput started`);
      } else {
        console.warn(`${TAG} startStream: cameraManager or session is null, skipping video output`);
      }

      console.info(`${TAG} startStream: starting encoder...`);
      startEncoder();
      console.info(`${TAG} startStream: encoder started`);

      this.streaming = true;
      this.notifyStatus();
      console.info(`${TAG} startStream END: success`);
    } catch (e) {
      const err = e as Error;
      console.error(`${TAG} startStream EXCEPTION: ${err.message}, stack=${err.stack}`);
      this.statusCallback?.({
        isStreaming: false,
        isPreviewReady: this.previewReady,
        statusText: rstr(this.context, 'cam_status_start_failed', err.message),
        clientCount: 0,
      });
    }
  }

  async stopStream(): Promise<void> {
    if (!this.streaming) return;
    try {
      this.streaming = false;

      stopEncoder();

      if (this.videoOutput) {
        this.videoOutput.stop();
        if (this.session) {
          this.session.beginConfig();
          this.session.removeOutput(this.videoOutput);
          await this.session.commitConfig();
        }
        this.videoOutput.release();
        this.videoOutput = null;
      }

      releaseEncoder();
      if (this.streamMode === 'rtmp' && this.rtmpPushManager) {
        await this.rtmpPushManager.disconnect();
        this.rtmpPushManager = null;
      } else {
        await this.rtspServer.stop();
      }

      this.notifyStatus();
      console.info(`${TAG} Stream stopped`);
    } catch (e) {
      console.error(`${TAG} stopStream failed: ${(e as Error).message}`);
    }
  }

  async switchCamera(): Promise<void> {
    const newFront = !this.useFrontCamera;
    const wasStreaming = this.streaming;

    if (wasStreaming) {
      await this.stopStream();
    }

    // Release current preview
    if (this.session) {
      await this.session.stop();
      this.session.release();
      this.session = null;
    }
    if (this.cameraInput) {
      this.cameraInput.close();
      this.cameraInput = null;
    }
    if (this.previewOutput) {
      this.previewOutput.release();
      this.previewOutput = null;
    }
    this.previewReady = false;

    this.useFrontCamera = newFront;

    // Re-init preview with new camera
    await this.initPreview(this.previewSurfaceId);

    if (wasStreaming) {
      await this.startStream();
    }

    this.notifyStatus();
  }

  private onEncodedFrame(frame: EncodedFrame): void {
    const data = new Uint8Array(frame.data);
    console.info(`${TAG} onEncodedFrame: size=${data.length}, pts=${frame.pts}, isKeyFrame=${frame.isKeyFrame}, flags=${frame.flags}`);
    this.parseAndSendNalu(data, frame.pts, frame.isKeyFrame);
  }

  private parseAndSendNalu(data: Uint8Array, pts: number, isKeyFrame: boolean): void {
    // Detect format: Annex-B (start codes 00 00 00 01 / 00 00 01) or AVCC (4-byte length prefix)
    const isAnnexB = (data.length >= 4 && data[0] === 0 && data[1] === 0 && data[2] === 0 && data[3] === 1) ||
                     (data.length >= 3 && data[0] === 0 && data[1] === 0 && data[2] === 1);

    if (isAnnexB) {
      this.parseAnnexBNalu(data, pts, isKeyFrame);
    } else {
      this.parseAvccNalu(data, pts, isKeyFrame);
    }
  }

  private parseAnnexBNalu(data: Uint8Array, pts: number, isKeyFrame: boolean): void {
    let offset = 0;
    while (offset < data.length) {
      // Find start code
      if (offset + 3 <= data.length && data[offset] === 0 && data[offset + 1] === 0 && data[offset + 2] === 1) {
        offset += 3;
      } else if (offset + 4 <= data.length && data[offset] === 0 && data[offset + 1] === 0 && data[offset + 2] === 0 && data[offset + 3] === 1) {
        offset += 4;
      } else {
        offset++;
        continue;
      }

      // Find next start code or end of data
      let end = offset;
      while (end < data.length) {
        if (end + 3 <= data.length && data[end] === 0 && data[end + 1] === 0 && data[end + 2] === 1) break;
        if (end + 4 <= data.length && data[end] === 0 && data[end + 1] === 0 && data[end + 2] === 0 && data[end + 3] === 1) break;
        end++;
      }

      if (end > offset) {
        const nalu = data.subarray(offset, end);
        const naluType = nalu.length > 0 ? nalu[0] & 0x1F : 0;
        console.info(`${TAG} NALU(Annex-B): type=${naluType}, len=${nalu.length}, isKeyFrame=${isKeyFrame}`);
        if (this.streamMode === 'rtmp' && this.rtmpPushManager) {
          this.rtmpPushManager.sendH264Data(nalu, pts);
        } else {
          this.rtspServer.sendH264Data(nalu, pts);
        }
        if (naluType === 7) {
          this.rtspServer.setSps(nalu);
          if (this.rtmpPushManager) this.rtmpPushManager.setSps(nalu);
        } else if (naluType === 8) {
          this.rtspServer.setPps(nalu);
          if (this.rtmpPushManager) this.rtmpPushManager.setPps(nalu);
        }
      }
      offset = end;
    }
  }

  private parseAvccNalu(data: Uint8Array, pts: number, isKeyFrame: boolean): void {
    let offset = 0;
    while (offset < data.length) {
      if (offset + 4 > data.length) break;

      const naluLength = (data[offset] << 24) | (data[offset + 1] << 16) | (data[offset + 2] << 8) | data[offset + 3];
      offset += 4;

      if (naluLength <= 0 || offset + naluLength > data.length) break;

      const nalu = data.subarray(offset, offset + naluLength);
      const naluType = nalu.length > 0 ? nalu[0] & 0x1F : 0;
      console.info(`${TAG} NALU(AVCC): type=${naluType}, len=${naluLength}, isKeyFrame=${isKeyFrame}`);
      if (this.streamMode === 'rtmp' && this.rtmpPushManager) {
        this.rtmpPushManager.sendH264Data(nalu, pts);
      } else {
        this.rtspServer.sendH264Data(nalu, pts);
      }
      if (naluType === 7) {
        this.rtspServer.setSps(nalu);
        if (this.rtmpPushManager) this.rtmpPushManager.setSps(nalu);
      } else if (naluType === 8) {
        this.rtspServer.setPps(nalu);
        if (this.rtmpPushManager) this.rtmpPushManager.setPps(nalu);
      }

      offset += naluLength;
    }
  }

  async release(): Promise<void> {
    await this.stopStream();

    if (this.session) {
      this.session.release();
      this.session = null;
    }
    if (this.cameraInput) {
      this.cameraInput.close();
      this.cameraInput = null;
    }
    if (this.previewOutput) {
      this.previewOutput.release();
      this.previewOutput = null;
    }
    this.cameraManager = null;
    this.previewReady = false;
  }
}
