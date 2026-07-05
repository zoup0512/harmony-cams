import { media } from '@kit.MediaKit';
import { image } from '@kit.ImageKit';
import { common } from '@kit.AbilityKit';
import { CameraDevice } from '../model/CameraDevice';
import { WlPlayer, WlComponentController, WlCompleteType, WlLoadStatus, WlCodecType, WlAudioInterruptType, WlAudioInterruptHint, WlTrackType } from '@ywl5320/libwlmedia';

export enum PlaybackRenderMode {
  TEXTURE_HARDWARE = 'TEXTURE_HARDWARE',
  SURFACE_SOFTWARE = 'SURFACE_SOFTWARE',
}

export class VideoStreamManager {
  private static instance: VideoStreamManager | null = null;
  private context: common.Context;
  private currentPlayer: media.AVPlayer | null = null;
  private lastDiagnosticMessage: string = '未开始播放';
  private isShuttingDown: boolean = false;
  private hasVideoOutput: boolean = false;
  private surfaceId: string | null = null;
  private videoWidth: number = 0;
  private videoHeight: number = 0;
  private wlPlayer: WlPlayer | null = null;
  private wlComponentController: WlComponentController | null = null;
  private usingWlPlayer: boolean = false;
  private wlPlayerPlaying: boolean = false;

  private constructor(context: common.Context) {
    this.context = context;
  }

  static getInstance(context: common.Context): VideoStreamManager {
    if (!VideoStreamManager.instance) {
      VideoStreamManager.instance = new VideoStreamManager(context);
    }
    return VideoStreamManager.instance;
  }

  getLastDiagnosticMessage(): string {
    return this.lastDiagnosticMessage;
  }

  hasVideoOutputFlag(): boolean {
    return this.hasVideoOutput;
  }

  getVideoWidth(): number {
    return this.videoWidth;
  }

  getVideoHeight(): number {
    return this.videoHeight;
  }

  setSurfaceId(surfaceId: string | null) {
    console.info(`VideoStreamManager setSurfaceId: surfaceId=${surfaceId}, hasPlayer=${this.currentPlayer !== null}`);
    this.surfaceId = surfaceId;
    if (this.currentPlayer && surfaceId) {
      this.currentPlayer.surfaceId = surfaceId;
      console.info(`VideoStreamManager setSurfaceId: applied to current player`);
    }
  }

  async startStream(camera: CameraDevice): Promise<media.AVPlayer | null> {
    const streamUrl = camera.rtspUrl;
    const streamType = streamUrl.split('://')[0] ?? 'unknown';
    console.info(`VideoStreamManager startStream BEGIN: cameraId=${camera.id}, name=${camera.name}, url=${streamUrl}, streamType=${streamType}, surfaceId=${this.surfaceId}, isShuttingDown=${this.isShuttingDown}`);
    this.lastDiagnosticMessage = `开始连接: ${streamUrl}`;
    if (streamUrl === '') {
      this.lastDiagnosticMessage = 'RTSP URL 为空';
      console.error(`VideoStreamManager startStream: URL is empty, aborting`);
      return null;
    }
    if (this.isShuttingDown) {
      this.lastDiagnosticMessage = '播放器正在关闭';
      console.error(`VideoStreamManager startStream: manager is shutting down, aborting`);
      return null;
    }

    try {
      console.info(`VideoStreamManager startStream: stopping previous player...`);
      await this.stopStream();
      this.hasVideoOutput = false;
      camera.isConnected = false;

      if (streamType === 'rtsp') {
        console.info(`VideoStreamManager startStream: RTSP detected, using WlPlayer (wlmedia)...`);
        return await this.startStreamWlPlayer(camera, streamUrl, streamType);
      }

      console.info(`VideoStreamManager startStream: creating AVPlayer...`);
      const player = await media.createAVPlayer();
      this.currentPlayer = player;
      console.info(`VideoStreamManager startStream: AVPlayer created successfully`);

      console.info(`VideoStreamManager startStream: setting url=${streamUrl}`);
      player.url = streamUrl;

      const startTime = Date.now();
      let preparedAt = 0;
      let playingAt = 0;

      let onInitialized: (() => void) | null = null;
      let onPrepared: (() => void) | null = null;
      let onError: ((msg: string) => void) | null = null;

      player.on('stateChange', (state: string, reason: media.StateChangeReason) => {
        const elapsed = Date.now() - startTime;
        console.info(`VideoStreamManager stateChange: state=${state}, reason=${reason}, elapsedMs=${elapsed}, url=${streamUrl}`);
        switch (state) {
          case 'idle':
            this.lastDiagnosticMessage = '空闲';
            break;
          case 'initialized':
            this.lastDiagnosticMessage = '已初始化';
            console.info(`VideoStreamManager stateChange=initialized: player.url=${player.url}, state=${player.state}`);
            if (this.surfaceId) {
              console.info(`VideoStreamManager: setting surfaceId=${this.surfaceId} in initialized state`);
              player.surfaceId = this.surfaceId;
            } else {
              console.warn(`VideoStreamManager: surfaceId is null in initialized state, video may not render`);
            }
            if (onInitialized) { onInitialized(); }
            break;
          case 'preparing':
            this.lastDiagnosticMessage = '正在准备...';
            break;
          case 'prepared':
            this.lastDiagnosticMessage = '已准备就绪';
            preparedAt = Date.now();
            console.info(`VideoStreamManager stateChange=prepared: prepareCostMs=${preparedAt - startTime}, duration=${player.duration}, state=${player.state}`);
            if (onPrepared) { onPrepared(); }
            break;
          case 'playing':
            this.lastDiagnosticMessage = '流已开始播放';
            camera.isConnected = true;
            this.hasVideoOutput = true;
            playingAt = Date.now();
            console.info(`VideoStreamManager stateChange=playing: playCostMs=${playingAt - startTime}, prepareToPlayMs=${playingAt - preparedAt}, state=${player.state}`);
            break;
          case 'paused':
            this.lastDiagnosticMessage = '已暂停';
            break;
          case 'completed':
            this.lastDiagnosticMessage = '流已结束';
            camera.isConnected = false;
            console.info(`VideoStreamManager stateChange=completed: stream ended, elapsedMs=${Date.now() - startTime}`);
            break;
          case 'stopped':
            this.lastDiagnosticMessage = '已停止';
            camera.isConnected = false;
            break;
          case 'released':
            this.lastDiagnosticMessage = '已释放';
            camera.isConnected = false;
            break;
          case 'error':
            this.lastDiagnosticMessage = '播放报错(可能URL/账号密码/编码不兼容)';
            camera.isConnected = false;
            console.error(`VideoStreamManager stateChange=error: reason=${reason}, elapsedMs=${Date.now() - startTime}, url=${streamUrl}`);
            if (onError) { onError(`stateChange error: reason=${reason}`); }
            break;
          default:
            console.warn(`VideoStreamManager stateChange: unknown state=${state}, reason=${reason}`);
            break;
        }
      });

      player.on('error', (err) => {
        const errJson = JSON.stringify(err);
        console.error(`VideoStreamManager onError: err=${errJson}, url=${streamUrl}, playerState=${player.state}`);
        this.lastDiagnosticMessage = `播放错误: ${err.message ?? 'unknown'}`;
        camera.isConnected = false;
        if (onError) { onError(err.message ?? 'unknown error'); }
      });

      player.on('videoSizeChange', (width: number, height: number) => {
        const elapsed = Date.now() - startTime;
        console.info(`VideoStreamManager videoSizeChange: ${width}x${height}, elapsedMs=${elapsed}, url=${streamUrl}`);
        this.videoWidth = width;
        this.videoHeight = height;
        this.hasVideoOutput = true;
      });

      console.info(`VideoStreamManager startStream: waiting for initialized state...`);
      let initTimeoutId: number = -1;
      await new Promise<void>((resolve, reject) => {
        initTimeoutId = setTimeout(() => {
          reject(new Error('timeout waiting for initialized state'));
        }, 10000);
        onInitialized = () => {
          clearTimeout(initTimeoutId);
          resolve();
        };
        onError = (msg: string) => {
          clearTimeout(initTimeoutId);
          reject(new Error(msg));
        };
      });
      onInitialized = null;
      onError = null;
      console.info(`VideoStreamManager startStream: initialized, calling prepare()...`);

      const prepareStart = Date.now();
      let prepareTimeoutId: number = -1;
      await new Promise<void>((resolve, reject) => {
        prepareTimeoutId = setTimeout(() => {
          reject(new Error('timeout waiting for prepared state (30s)'));
        }, 30000);
        onPrepared = () => {
          clearTimeout(prepareTimeoutId);
          resolve();
        };
        onError = (msg: string) => {
          clearTimeout(prepareTimeoutId);
          reject(new Error(msg));
        };
        player.prepare().catch((e: Error) => {
          clearTimeout(prepareTimeoutId);
          console.error(`VideoStreamManager prepare() rejected: ${e.message}`);
          reject(e);
        });
      });
      onPrepared = null;
      onError = null;
      const prepareCost = Date.now() - prepareStart;
      console.info(`VideoStreamManager startStream: prepare() done, costMs=${prepareCost}, state=${player.state}`);

      console.info(`VideoStreamManager startStream: calling play()...`);
      const playStart = Date.now();
      await player.play();
      const playCost = Date.now() - playStart;
      console.info(`VideoStreamManager startStream: play() done, costMs=${playCost}, state=${player.state}`);

      this.lastDiagnosticMessage = '已发起播放';
      console.info(`VideoStreamManager startStream END: success, totalCostMs=${Date.now() - startTime}, streamType=${streamType}`);
      return player;
    } catch (e) {
      const errMsg = (e as Error).message;
      const errStack = (e as Error).stack;
      console.error(`VideoStreamManager startStream EXCEPTION: msg=${errMsg}, stack=${errStack}, url=${streamUrl}, streamType=${streamType}`);
      this.lastDiagnosticMessage = `启动异常: ${errMsg}`;
      return null;
    }
  }

  async stopStream(): Promise<void> {
    if (this.usingWlPlayer) {
      console.info(`VideoStreamManager stopStream: stopping WlPlayer...`);
      try {
        this.wlPlayer?.stop();
        this.wlPlayer?.release();
      } catch (e) {
        console.error(`VideoStreamManager stopStream: WlPlayer stop failed: ${(e as Error).message}`);
      }
      this.wlPlayer = null;
      this.wlComponentController = null;
      this.usingWlPlayer = false;
      this.wlPlayerPlaying = false;
      this.hasVideoOutput = false;
      this.videoWidth = 0;
      this.videoHeight = 0;
      this.lastDiagnosticMessage = '已手动停止';
      return;
    }
    const player = this.currentPlayer;
    if (!player) return;
    try {
      await player.stop();
      await player.release();
    } catch (e) {
      console.error(`VideoStreamManager stopStream failed: ${(e as Error).message}`);
    }
    this.currentPlayer = null;
    this.hasVideoOutput = false;
    this.videoWidth = 0;
    this.videoHeight = 0;
    this.lastDiagnosticMessage = '已手动停止';
  }

  async play(): Promise<void> {
    if (this.usingWlPlayer) {
      try { this.wlPlayer?.resume(); } catch (e) { console.error(`VideoStreamManager play: WlPlayer resume failed: ${(e as Error).message}`); }
      return;
    }
    if (this.currentPlayer) {
      const state = this.currentPlayer.state;
      if (state !== 'prepared' && state !== 'paused' && state !== 'completed' && state !== 'playing') {
        console.warn(`VideoStreamManager play() skipped: player state=${state}, not ready`);
        return;
      }
      try {
        await this.currentPlayer.play();
      } catch (e) {
        console.error(`VideoStreamManager play failed: ${(e as Error).message}`);
      }
    }
  }

  async pause(): Promise<void> {
    if (this.usingWlPlayer) {
      try { this.wlPlayer?.pause(); } catch (e) { console.error(`VideoStreamManager pause: WlPlayer pause failed: ${(e as Error).message}`); }
      return;
    }
    if (this.currentPlayer) {
      try {
        await this.currentPlayer.pause();
      } catch (e) {
        console.error(`VideoStreamManager pause failed: ${(e as Error).message}`);
      }
    }
  }

  setVolume(volume: number): void {
    if (this.usingWlPlayer) {
      this.wlPlayer?.setVolume(volume * 2);
      return;
    }
    if (this.currentPlayer) {
      this.currentPlayer.setVolume(volume / 100);
    }
  }

  getVolume(): number {
    if (this.usingWlPlayer) {
      return this.wlPlayer ? Math.round(this.wlPlayer.getVolume() / 2) : 0;
    }
    if (this.currentPlayer) {
      return Math.round((this.currentPlayer.state === 'playing' ? 100 : 0));
    }
    return -1;
  }

  isPlaying(): boolean {
    if (this.usingWlPlayer) {
      return this.wlPlayerPlaying;
    }
    if (this.currentPlayer) {
      return this.currentPlayer.state === 'playing';
    }
    return false;
  }

  getSurfaceId(): string | null {
    return this.surfaceId;
  }

  async captureSurfaceFrame(): Promise<image.PixelMap | null> {
    const sid = this.surfaceId;
    const hasPlayer = this.currentPlayer !== null || this.usingWlPlayer;
    const playerState = this.usingWlPlayer ? 'wlplayer' : (this.currentPlayer ? this.currentPlayer.state : 'null');
    console.info(`VideoStreamManager captureSurfaceFrame: BEGIN surfaceId='${sid}', hasPlayer=${hasPlayer}, playerState=${playerState}, hasVideoOutput=${this.hasVideoOutput}`);
    if (!sid || !hasPlayer || !this.hasVideoOutput) {
      console.warn(`VideoStreamManager captureSurfaceFrame: SKIPPED surfaceId='${sid}', hasPlayer=${hasPlayer}, hasVideoOutput=${this.hasVideoOutput}`);
      return null;
    }
    try {
      console.info(`VideoStreamManager captureSurfaceFrame: calling image.createPixelMapFromSurface('${sid}')...`);
      const pixelMap = await image.createPixelMapFromSurface(sid);
      const info = pixelMap.getImageInfoSync();
      console.info(`VideoStreamManager captureSurfaceFrame: SUCCESS size=${info.size.width}x${info.size.height}`);
      return pixelMap;
    } catch (e) {
      console.error(`VideoStreamManager captureSurfaceFrame FAILED: ${(e as Error).message}, stack=${(e as Error).stack}`);
      return null;
    }
  }

  async shutdown(): Promise<void> {
    this.isShuttingDown = true;
    await this.stopStream();
    this.isShuttingDown = false;
    VideoStreamManager.instance = null;
  }

  private async startStreamWlPlayer(camera: CameraDevice, streamUrl: string, streamType: string): Promise<media.AVPlayer | null> {
    const startTime = Date.now();
    console.info(`VideoStreamManager startStreamWlPlayer BEGIN: url=${streamUrl}, surfaceId=${this.surfaceId}`);

    if (!this.surfaceId) {
      this.lastDiagnosticMessage = 'SurfaceId 未就绪，无法播放 RTSP';
      console.error(`VideoStreamManager startStreamWlPlayer: surfaceId is null`);
      return null;
    }

    try {
      const player = new WlPlayer();
      this.wlPlayer = player;
      this.usingWlPlayer = true;

      const wlComponentController = new WlComponentController(player);
      this.wlComponentController = wlComponentController;
      player.setClearLastVideoFrame(wlComponentController.getUniqueNum(), false);
      player.setCodecType(WlCodecType.WL_CODEC_SOFT);
      player.setBufferSize(1, 15);
      player.setTimeOut(15);

      let onPrepared: (() => void) | null = null;
      let onError: ((msg: string) => void) | null = null;
      let onFirstFrame: (() => void) | null = null;

      player.setOnMediaInfoListener({
        onPrepared: (): void => {
          console.info(`VideoStreamManager WlPlayer onPrepared`);
          this.lastDiagnosticMessage = 'RTSP流已准备就绪';
          if (onPrepared) { onPrepared(); }
        },
        onTimeInfo: (currentTime: number, bufferTime: number): void => {
        },
        onComplete: (wlCompleteType: WlCompleteType, msg: string): void => {
          console.info(`VideoStreamManager WlPlayer onComplete: type=${wlCompleteType}, msg=${msg}`);
          if (wlCompleteType === WlCompleteType.WL_COMPLETE_ERROR) {
            this.lastDiagnosticMessage = `RTSP播放错误: ${msg}`;
            this.wlPlayerPlaying = false;
            camera.isConnected = false;
            if (onError) { onError(msg); }
          } else if (wlCompleteType === WlCompleteType.WL_COMPLETE_HANDLE) {
            this.lastDiagnosticMessage = 'RTSP流已停止';
            this.wlPlayerPlaying = false;
            camera.isConnected = false;
          } else if (wlCompleteType === WlCompleteType.WL_COMPLETE_TIMEOUT) {
            this.lastDiagnosticMessage = 'RTSP连接超时';
            this.wlPlayerPlaying = false;
            camera.isConnected = false;
            if (onError) { onError('RTSP连接超时'); }
          }
        },
        onLoadInfo: (wlLoadStatus: WlLoadStatus, progress: number, speed: number): void => {
          if (wlLoadStatus === WlLoadStatus.WL_LOADING_STATUS_START) {
            this.lastDiagnosticMessage = '正在加载RTSP流...';
          } else if (wlLoadStatus === WlLoadStatus.WL_LOADING_STATUS_FINISH) {
            this.lastDiagnosticMessage = 'RTSP流加载完成';
          }
        },
        onFirstFrameRendered: (): void => {
          console.info(`VideoStreamManager WlPlayer onFirstFrameRendered`);
          this.hasVideoOutput = true;
          this.wlPlayerPlaying = true;
          camera.isConnected = true;
          this.lastDiagnosticMessage = 'RTSP流已开始播放';
          if (onFirstFrame) { onFirstFrame(); }
        },
        onSeekFinish: (): void => {
        },
        onAudioInterrupt: (type: WlAudioInterruptType, hint: WlAudioInterruptHint): void => {
        },
        onAutoPlay: (): void => {
        },
        onDeEncryptData: (mediaType: WlTrackType, data: Uint8Array): Uint8Array | null => {
          return null;
        },
        onTakePicture: (pixelMap: image.PixelMap): void => {
        }
      });

      player.setSurface(this.surfaceId, '#000000FF', wlComponentController.getUniqueNum());
      player.setSource(streamUrl);
      console.info(`VideoStreamManager startStreamWlPlayer: source set, calling prepare...`);
      player.prepare();

      console.info(`VideoStreamManager startStreamWlPlayer: waiting for prepared state (15s timeout)...`);
      let timeoutId: number = -1;
      await new Promise<void>((resolve, reject) => {
        timeoutId = setTimeout(() => {
          reject(new Error('timeout waiting for WlPlayer prepared state (15s)'));
        }, 15000);
        onPrepared = () => {
          clearTimeout(timeoutId);
          resolve();
        };
        onError = (msg: string) => {
          clearTimeout(timeoutId);
          reject(new Error(msg));
        };
      });
      onPrepared = null;
      onError = null;

      console.info(`VideoStreamManager startStreamWlPlayer: prepared, calling start...`);
      player.start();

      console.info(`VideoStreamManager startStreamWlPlayer: waiting for first frame (15s timeout)...`);
      await new Promise<void>((resolve, reject) => {
        timeoutId = setTimeout(() => {
          reject(new Error('timeout waiting for WlPlayer first frame (15s)'));
        }, 15000);
        onFirstFrame = () => {
          clearTimeout(timeoutId);
          resolve();
        };
        onError = (msg: string) => {
          clearTimeout(timeoutId);
          reject(new Error(msg));
        };
      });
      onFirstFrame = null;
      onError = null;

      const totalCost = Date.now() - startTime;
      console.info(`VideoStreamManager startStreamWlPlayer END: success, totalCostMs=${totalCost}, streamType=${streamType}`);
      return { state: 'playing' } as media.AVPlayer;
    } catch (e) {
      const errMsg = (e as Error).message;
      console.error(`VideoStreamManager startStreamWlPlayer EXCEPTION: msg=${errMsg}, url=${streamUrl}`);
      this.lastDiagnosticMessage = `RTSP启动异常: ${errMsg}`;
      this.usingWlPlayer = false;
      this.wlPlayerPlaying = false;
      try { this.wlPlayer?.release(); } catch (e) { console.error(`VideoStreamManager startStreamWlPlayer: release failed: ${(e as Error).message}`); }
      this.wlPlayer = null;
      this.wlComponentController = null;
      return null;
    }
  }
}
