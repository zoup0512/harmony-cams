import { media } from '@kit.MediaKit';
import { common } from '@kit.AbilityKit';
import { CameraDevice } from '../model/CameraDevice';

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

  setSurfaceId(surfaceId: string | null) {
    this.surfaceId = surfaceId;
    if (this.currentPlayer && surfaceId) {
      this.currentPlayer.surfaceId = surfaceId;
    }
  }

  async startStream(camera: CameraDevice): Promise<media.AVPlayer | null> {
    this.lastDiagnosticMessage = `开始连接: ${camera.rtspUrl}`;
    if (camera.rtspUrl === '') {
      this.lastDiagnosticMessage = 'RTSP URL 为空';
      return null;
    }
    if (this.isShuttingDown) {
      this.lastDiagnosticMessage = '播放器正在关闭';
      return null;
    }

    try {
      await this.stopStream();
      this.hasVideoOutput = false;
      camera.isConnected = false;

      const player = await media.createAVPlayer();
      this.currentPlayer = player;

      player.url = camera.rtspUrl;
      if (this.surfaceId) {
        player.surfaceId = this.surfaceId;
      }

      player.on('stateChange', (state: string, reason: media.StateChangeReason) => {
        console.info(`VideoStreamManager stateChange: state=${state}, reason=${reason}`);
        switch (state) {
          case 'idle':
            this.lastDiagnosticMessage = '空闲';
            break;
          case 'initialized':
            this.lastDiagnosticMessage = '已初始化';
            break;
          case 'preparing':
            this.lastDiagnosticMessage = '正在准备...';
            break;
          case 'prepared':
            this.lastDiagnosticMessage = '已准备就绪';
            break;
          case 'playing':
            this.lastDiagnosticMessage = '流已开始播放';
            camera.isConnected = true;
            this.hasVideoOutput = true;
            break;
          case 'paused':
            this.lastDiagnosticMessage = '已暂停';
            break;
          case 'completed':
            this.lastDiagnosticMessage = '流已结束';
            camera.isConnected = false;
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
            break;
        }
      });

      player.on('error', (err) => {
        console.error(`VideoStreamManager error: ${JSON.stringify(err)}`);
        this.lastDiagnosticMessage = `播放错误: ${err.message ?? 'unknown'}`;
        camera.isConnected = false;
      });

      player.on('videoSizeChange', (width: number, height: number) => {
        console.info(`VideoStreamManager videoSizeChange: ${width}x${height}`);
        this.hasVideoOutput = true;
      });

      await player.prepare();
      await player.play();

      this.lastDiagnosticMessage = '已发起播放';
      return player;
    } catch (e) {
      console.error(`VideoStreamManager startStream failed: ${(e as Error).message}`);
      this.lastDiagnosticMessage = `启动异常: ${(e as Error).message}`;
      return null;
    }
  }

  async stopStream(): Promise<void> {
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
    this.lastDiagnosticMessage = '已手动停止';
  }

  async play(): Promise<void> {
    if (this.currentPlayer) {
      await this.currentPlayer.play();
    }
  }

  async pause(): Promise<void> {
    if (this.currentPlayer) {
      await this.currentPlayer.pause();
    }
  }

  setVolume(volume: number): void {
    if (this.currentPlayer) {
      this.currentPlayer.setVolume(volume / 100);
    }
  }

  getVolume(): number {
    if (this.currentPlayer) {
      return Math.round((this.currentPlayer.state === 'playing' ? 100 : 0));
    }
    return -1;
  }

  isPlaying(): boolean {
    if (this.currentPlayer) {
      return this.currentPlayer.state === 'playing';
    }
    return false;
  }

  async shutdown(): Promise<void> {
    this.isShuttingDown = true;
    await this.stopStream();
    this.isShuttingDown = false;
    VideoStreamManager.instance = null;
  }
}
