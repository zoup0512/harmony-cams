import { RecordingFile, readableFileSize } from '../model/RecordingFile';
import { buildPhoneRtspAddress } from '../utils/NetworkUtils';
import { CameraStreamManager, StreamStatus } from '../utils/CameraStreamManager';
import { common } from '@kit.AbilityKit';

interface ThemeOptionItem {
  value: string;
  label: string;
}

interface SnapshotGroup {
  label: string;
  items: RecordingFile[];
}

function formatDateGroupLabel(timestamp: number, context: common.Context): string {
  const date = new Date(timestamp);
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const yesterdayStart = new Date(todayStart.getTime() - 86400000);
  const dateStart = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  if (dateStart.getTime() === todayStart.getTime()) return context.resourceManager.getStringByNameSync('date_today');
  if (dateStart.getTime() === yesterdayStart.getTime()) return context.resourceManager.getStringByNameSync('date_yesterday');
  return context.resourceManager.getStringByNameSync('date_format_long').replace('%d', String(date.getFullYear())).replace('%d', String(date.getMonth() + 1)).replace('%d', String(date.getDate()));
}

function groupSnapshotsByDate(snapshots: RecordingFile[], context: common.Context): SnapshotGroup[] {
  const groups: SnapshotGroup[] = [];
  for (const s of snapshots) {
    const ts = s.startTime ?? 0;
    const label = ts > 0 ? formatDateGroupLabel(ts, context) : context.resourceManager.getStringByNameSync('date_unknown');
    let group = groups.find(g => g.label === label);
    if (!group) {
      group = { label, items: [] };
      groups.push(group);
    }
    group.items.push(s);
  }
  return groups;
}

@Component
export struct ActivityStreamScreen {
  recordings: RecordingFile[] = [];
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  accentColor: string = '#20B486';
  onPrimaryColor: string = '#FFFFFF';
  surfaceVariantColor: string = '#F0F1F5';
  onOpenRecording: (file: RecordingFile) => void = () => {};
  rtmpUrlInput: string = '';
  rtmpStreamKeyInput: string = '';
  rtmpUsernameInput: string = '';
  rtmpPasswordInput: string = '';
  @State selectedStreamTool: string = 'rtsp';
  @State isStreaming: boolean = false;
  @State useFrontCamera: boolean = false;
  @State statusText: string = '';
  @State rtspAddress: string = buildPhoneRtspAddress();
  @State clientCount: number = 0;
  private streamManager: CameraStreamManager | null = null;
  private xComponentController: XComponentController = new XComponentController();
  private previewSurfaceId: string = '';

  buildRtmpUrl(): string {
    const host = this.rtmpUrlInput.replace(/^rtmp:\/\//i, '');
    return `rtmp://${host}/${this.rtmpStreamKeyInput}`;
  }

  streamTargetText(): Resource | string {
    if (this.selectedStreamTool === 'rtmp') {
      return this.rtmpUrlInput
        ? $r('app.string.stream_target', this.buildRtmpUrl())
        : $r('app.string.rtmp_target_not_configured');
    }
    return $r('app.string.stream_address', this.rtspAddress);
  }

  aboutToAppear(): void {
    this.rtspAddress = buildPhoneRtspAddress();
    const context = getContext(this) as common.Context;
    this.streamManager = new CameraStreamManager(context);
    this.streamManager.setStatusCallback((status: StreamStatus) => {
      this.isStreaming = status.isStreaming;
      this.statusText = status.statusText;
      this.clientCount = status.clientCount;
    });
  }

  aboutToDisappear(): void {
    if (this.streamManager) {
      this.streamManager.release();
      this.streamManager = null;
    }
  }

  build(): void {
    Column() {
        Row({ space: 10 }) {
          Text($r('app.string.rtsp_server'))
              .fontSize(14)
              .fontColor(this.selectedStreamTool === 'rtsp' ? this.onPrimaryColor : this.onSurfaceVariantColor)
              .fontWeight(this.selectedStreamTool === 'rtsp' ? FontWeight.Medium : FontWeight.Normal)
              .layoutWeight(1)
              .textAlign(TextAlign.Center)
              .padding({ top: 10, bottom: 10 })
              .backgroundColor(this.selectedStreamTool === 'rtsp' ? this.accentColor : this.surfaceVariantColor)
              .borderRadius(12)
              .onClick(() => {
                this.selectedStreamTool = 'rtsp';
                if (this.streamManager) {
                  this.streamManager.setStreamMode('rtsp');
                }
              })

            Text($r('app.string.rtmp_stream'))
              .fontSize(14)
              .fontColor(this.selectedStreamTool === 'rtmp' ? this.onPrimaryColor : this.onSurfaceVariantColor)
              .fontWeight(this.selectedStreamTool === 'rtmp' ? FontWeight.Medium : FontWeight.Normal)
              .layoutWeight(1)
              .textAlign(TextAlign.Center)
              .padding({ top: 10, bottom: 10 })
              .backgroundColor(this.selectedStreamTool === 'rtmp' ? this.accentColor : this.surfaceVariantColor)
              .borderRadius(12)
              .onClick(() => {
                this.selectedStreamTool = 'rtmp';
                if (this.streamManager) {
                  this.streamManager.setStreamMode('rtmp');
                  this.streamManager.setRtmpConfig(
                    this.rtmpUrlInput,
                    this.rtmpStreamKeyInput,
                    this.rtmpUsernameInput,
                    this.rtmpPasswordInput,
                  );
                }
              })
          }
          .width('100%')

          // Phone camera streaming card
          Column() {
            Row() {
              Column() {
                Text(this.selectedStreamTool === 'rtmp' ? $r('app.string.rtmp_stream_to_server') : $r('app.string.phone_rtsp_stream'))
                  .fontSize(16)
                  .fontColor(this.onSurfaceColor)
                  .fontWeight(FontWeight.Bold)
                Text(this.statusText)
                  .fontSize(12)
                  .fontColor(this.onSurfaceVariantColor)
                  .margin({ top: 4 })
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)

              Text(this.isStreaming ? $r('app.string.status_running') : $r('app.string.status_standby_btn'))
                .fontSize(12)
                .fontColor(this.isStreaming ? this.onPrimaryColor : this.onSurfaceVariantColor)
                .padding({ left: 12, right: 12, top: 6, bottom: 6 })
                .backgroundColor(this.isStreaming ? this.accentColor : this.surfaceVariantColor)
                .borderRadius(999)
            }
            .width('100%')
            .justifyContent(FlexAlign.SpaceBetween)

            Stack() {
              XComponent({
                id: 'rtspCameraPreview',
                type: XComponentType.SURFACE,
                controller: this.xComponentController
              })
                .onLoad(() => {
                  this.previewSurfaceId = this.xComponentController.getXComponentSurfaceId();
                  console.info(`ActivityStreamScreen XComponent.onLoad: surfaceId=${this.previewSurfaceId}, hasStreamManager=${this.streamManager !== null}`);
                  if (this.streamManager && this.previewSurfaceId) {
                    this.streamManager.initPreview(this.previewSurfaceId).then(() => {
                      console.info(`ActivityStreamScreen XComponent.onLoad: initPreview completed`);
                    }).catch((e: Error) => {
                      console.error(`ActivityStreamScreen XComponent.onLoad: initPreview failed: ${e.message}`);
                    });
                  } else {
                    console.error(`ActivityStreamScreen XComponent.onLoad: streamManager=${this.streamManager !== null}, surfaceId='${this.previewSurfaceId}'`);
                  }
                })
                .width('100%')
                .height('100%')

              if (!this.isStreaming && this.statusText === '') {
                Text($r('app.string.preview_area'))
                  .fontSize(16)
                  .fontColor('#FFFFFF80')
              }
            }
            .width('100%')
            .aspectRatio(4 / 3)
            .borderRadius(16)
            .backgroundColor('#000000')
            .margin({ top: 12 })
            .clip(true)

            Text(this.streamTargetText())
              .fontSize(12)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ top: 12 })

            Row({ space: 10 }) {
              Button(this.useFrontCamera ? $r('app.string.switch_to_back') : $r('app.string.switch_to_front'))
                .layoutWeight(1)
                .onClick(() => {
                  if (this.streamManager) {
                    this.streamManager.switchCamera().then(() => {
                      this.useFrontCamera = !this.useFrontCamera;
                    });
                  }
                })
              Button(this.isStreaming ? $r('app.string.stop_stream') : $r('app.string.start_stream'))
                .layoutWeight(1)
                .onClick(() => {
                  if (this.isStreaming) {
                    this.streamManager?.stopStream();
                  } else {
                    if (this.selectedStreamTool === 'rtmp' && this.streamManager) {
                      this.streamManager.setStreamMode('rtmp');
                      this.streamManager.setRtmpConfig(
                        this.rtmpUrlInput,
                        this.rtmpStreamKeyInput,
                        this.rtmpUsernameInput,
                        this.rtmpPasswordInput,
                      );
                    }
                    this.streamManager?.startStream();
                  }
                })
            }
            .width('100%')
            .margin({ top: 12 })
          }
          .width('100%')
          .borderRadius(20)
          .backgroundColor(this.surfaceColor)
          .padding(16)
          .margin({ top: 14 })

          Text($r('app.string.activity_log'))
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)
            .fontWeight(FontWeight.Medium)
            .margin({ top: 14, bottom: 4 })

          if (this.recordings.length === 0) {
            Text($r('app.string.no_activity'))
              .fontSize(16)
              .fontColor(this.onSurfaceVariantColor)
              .width('100%')
              .textAlign(TextAlign.Center)
              .padding(32)
          } else {
            ForEach(this.recordings, (recording: RecordingFile) => {
              Row() {
                Column() {
                  Text(recording.fileName)
                    .fontSize(16)
                    .fontColor(this.onSurfaceColor)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                  Text(recording.filePath)
                    .fontSize(12)
                    .fontColor(this.onSurfaceVariantColor)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .margin({ top: 4 })
                }
                .layoutWeight(1)
                .alignItems(HorizontalAlign.Start)

                Text($r('app.string.action_view'))
                  .fontSize(16)
                  .fontColor(this.accentColor)
                  .fontWeight(FontWeight.Medium)
                  .margin({ left: 10 })
              }
              .width('100%')
              .borderRadius(18)
              .backgroundColor(this.surfaceColor)
              .padding(16)
              .margin({ top: 14 })
              .onClick(() => this.onOpenRecording(recording))
            })
          }
        }
        .width('100%')
        .padding({ left: 18, right: 18, top: 12, bottom: 100 })
        .backgroundColor(this.bgColor)
    }
  }

@Component
export struct SnapshotScreen {
  @Prop snapshots: RecordingFile[] = [];
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  accentColor: string = '#20B486';
  onOpenSnapshot: (file: RecordingFile) => void = () => {};
  onDeleteSnapshot: (file: RecordingFile) => void = () => {};
  onShareSnapshot: (file: RecordingFile) => void = () => {};

  build(): void {
    Scroll() {
      Column() {
        if (this.snapshots.length === 0) {
          Text($r('app.string.no_snapshots'))
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)
        } else {
        ForEach(groupSnapshotsByDate(this.snapshots, getContext(this)), (group: SnapshotGroup) => {
          Text(group.label)
            .fontSize(16)
            .fontColor(this.onSurfaceColor)
            .fontWeight(FontWeight.Bold)
            .width('100%')
            .margin({ top: 16, bottom: 8 })

          ForEach(group.items, (snapshot: RecordingFile) => {
            Stack({ alignContent: Alignment.BottomEnd }) {
              Image(`file://${snapshot.filePath}`)
                .width('100%')
                .aspectRatio(16 / 9)
                .borderRadius(14)
                .objectFit(ImageFit.Cover)
                .backgroundColor(this.surfaceColor)
                .onError((err) => {
                  console.error(`SnapshotScreen Image onError: filePath='${snapshot.filePath}'`);
                })
                .onComplete(() => {
                  console.info(`SnapshotScreen Image onComplete: filePath='${snapshot.filePath}'`);
                })

              Row() {
                Text('📤')
                  .fontSize(18)
                  .margin({ right: 12 })
                  .onClick(() => this.onShareSnapshot(snapshot))
                Text('🗑')
                  .fontSize(18)
                  .onClick(() => this.onDeleteSnapshot(snapshot))
              }
              .padding({ right: 12, bottom: 10 })
            }
            .width('100%')
            .borderRadius(14)
            .margin({ bottom: 12 })
            .onClick(() => this.onOpenSnapshot(snapshot))
            .gesture(
              LongPressGesture()
                .onAction(() => this.onDeleteSnapshot(snapshot))
            )
          }, (snapshot: RecordingFile) => snapshot.filePath)
        }, (group: SnapshotGroup) => `${group.label}_${group.items.map(i => i.filePath).join(',')}`)
        }
      }
      .width('100%')
      .padding({ left: 16, top: 12, right: 16, bottom: 100 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.bgColor)
  }
}

@Component
export struct SettingsHomeScreen {
  storagePathInput: string = '';
  recordingQualityInput: string = '1080p';
  recordingDurationInput: string = '30';
  appearanceThemeMode: string = 'light';
  rtmpUrlInput: string = '';
  rtmpStreamKeyInput: string = '';
  rtmpUsernameInput: string = '';
  rtmpPasswordInput: string = '';
  rtmpAudioEnabled: boolean = true;
  rtmpAutoStreamEnabled: boolean = false;
  @Prop bgColor: string = '#F7F8FA';
  @Prop surfaceColor: string = '#FFFFFF';
  @Prop surfaceVariantColor: string = '#F0F1F5';
  @Prop onSurfaceColor: string = '#1B1B1D';
  @Prop onSurfaceVariantColor: string = '#6B6F76';
  @Prop accentColor: string = '#20B486';
  @Prop onPrimaryColor: string = '#FFFFFF';
  @Prop outlineVariantColor: string = '#E0E2E8';
  onStoragePathChange: (v: string) => void = () => {};
  onRecordingQualityChange: (v: string) => void = () => {};
  onRecordingDurationChange: (v: string) => void = () => {};
  onThemeModeChange: (v: string) => void = () => {};
  onRtmpUrlChange: (v: string) => void = () => {};
  onRtmpStreamKeyChange: (v: string) => void = () => {};
  onRtmpUsernameChange: (v: string) => void = () => {};
  onRtmpPasswordChange: (v: string) => void = () => {};
  onRtmpAudioEnabledChange: (v: boolean) => void = () => {};
  onRtmpAutoStreamChange: (v: boolean) => void = () => {};
  onClearRecordings: () => void = () => {};
  onManageRtmpSettings: () => void = () => {};
  @State showPassword: boolean = false;
  @Prop bgPlaybackEnabled: boolean = false;
  onBgPlaybackChange: (enabled: boolean) => void = () => {};
  appLockEnabled: boolean = false;
  onAppLockChange: (enabled: boolean) => void = () => {};
  onSelectStoragePath: () => void = () => {};

  build(): void {
    Column() {
        // Appearance
        Text($r('app.string.section_appearance'))
          .fontSize(16)
          .fontColor(this.onSurfaceVariantColor)
          .fontWeight(FontWeight.Medium)
          .margin({ top: 16, bottom: 10 })

        // Theme selector
        Row() {
          Text($r('app.string.settings_theme'))
            .fontSize(16)
            .fontColor(this.onSurfaceColor)
            .fontWeight(FontWeight.Bold)
            .width(46)

          Row({ space: 3 }) {
            ForEach([
              { value: 'system', label: getContext(this).resourceManager.getStringByNameSync('theme_system') },
              { value: 'light', label: getContext(this).resourceManager.getStringByNameSync('theme_light') },
              { value: 'dark', label: getContext(this).resourceManager.getStringByNameSync('theme_dark') },
            ], (item: ThemeOptionItem) => {
              Text(item.label)
                .fontSize(14)
                .fontColor(this.onSurfaceColor)
                .fontWeight(this.appearanceThemeMode === item.value ? FontWeight.Bold : FontWeight.Medium)
                .layoutWeight(1)
                .height(34)
                .textAlign(TextAlign.Center)
                .backgroundColor(this.appearanceThemeMode === item.value ? this.surfaceColor : Color.Transparent)
                .borderRadius(10)
                .onClick(() => this.onThemeModeChange(item.value))
            })
          }
          .layoutWeight(1)
          .backgroundColor(this.surfaceVariantColor)
          .borderRadius(12)
          .padding({ left: 2, right: 2, top: 2, bottom: 2 })
        }
        .width('100%')
        .borderRadius(24)
        .backgroundColor(this.surfaceColor)
        .padding({ left: 18, right: 18, top: 16, bottom: 16 })

        // Device options
        Text($r('app.string.section_device_options'))
          .fontSize(16)
          .fontColor(this.onSurfaceVariantColor)
          .fontWeight(FontWeight.Medium)
          .margin({ top: 16, bottom: 10 })

        Column() {
          // Storage path
          Column() {
            Text($r('app.string.settings_storage_path'))
              .fontSize(13)
              .fontColor(this.onSurfaceVariantColor)
            Row() {
              Text(this.storagePathInput || $r('app.string.storage_path_hint'))
                .fontSize(14)
                .fontColor(this.storagePathInput ? this.onSurfaceColor : this.onSurfaceVariantColor)
                .layoutWeight(1)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              Text($r('app.string.action_select'))
                .fontSize(12)
                .fontColor(this.accentColor)
                .fontWeight(FontWeight.Medium)
            }
            .width('100%')
            .borderRadius(12)
            .border({ width: 1, color: this.outlineVariantColor })
            .backgroundColor(this.surfaceVariantColor)
            .padding({ left: 14, right: 14, top: 12, bottom: 12 })
            .margin({ top: 6 })
            .onClick(() => this.onSelectStoragePath())
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 10, bottom: 10 })

          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })

          // Recording quality
          Column() {
            Text($r('app.string.settings_recording_quality'))
              .fontSize(13)
              .fontColor(this.onSurfaceVariantColor)
            Row({ space: 8 }) {
              ForEach(['4K', '1080p', '720p', '480p'], (option: string) => {
                Text(option)
                  .fontSize(14)
                  .fontColor(this.recordingQualityInput === option ? this.onPrimaryColor : this.onSurfaceVariantColor)
                  .fontWeight(this.recordingQualityInput === option ? FontWeight.Medium : FontWeight.Normal)
                  .layoutWeight(1)
                  .textAlign(TextAlign.Center)
                  .padding({ top: 8, bottom: 8 })
                  .backgroundColor(this.recordingQualityInput === option ? this.accentColor : this.surfaceVariantColor)
                  .borderRadius(20)
                  .onClick(() => this.onRecordingQualityChange(option))
              })
            }
            .width('100%')
            .margin({ top: 8 })
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 10, bottom: 10 })

          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })

          // Recording duration
          Column() {
            Text($r('app.string.settings_recording_duration'))
              .fontSize(13)
              .fontColor(this.onSurfaceVariantColor)
            TextInput({ text: this.recordingDurationInput, placeholder: $r('app.string.duration_hint') })
              .fontSize(14)
              .fontColor(this.onSurfaceColor)
              .backgroundColor(Color.Transparent)
              .width('100%')
              .margin({ top: 6 })
              .onChange((v: string) => this.onRecordingDurationChange(v))
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 10, bottom: 10 })
        }
        .width('100%')
        .borderRadius(20)
        .backgroundColor(this.surfaceColor)

        // Background playback toggle
        Row() {
          Column() {
            Text($r('app.string.settings_bg_playback'))
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Medium)
            Text($r('app.string.settings_bg_playback_desc'))
              .fontSize(12)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ top: 4 })
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
          Toggle({ type: ToggleType.Switch, isOn: this.bgPlaybackEnabled })
            .onChange((v: boolean) => { this.onBgPlaybackChange(v); })
        }
        .width('100%')
        .borderRadius(20)
        .backgroundColor(this.surfaceColor)
        .padding(16)
        .margin({ top: 10 })

        // RTMP
        Text($r('app.string.section_rtmp'))
          .fontSize(16)
          .fontColor(this.onSurfaceVariantColor)
          .fontWeight(FontWeight.Medium)
          .margin({ top: 16, bottom: 10 })

        Column() {
          this.SettingsTextField($r('app.string.rtmp_server_addr'), $r('app.string.rtmp_server_hint'), this.rtmpUrlInput, this.onRtmpUrlChange, false)
          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })
          this.SettingsTextField($r('app.string.rtmp_stream_key'), $r('app.string.rtmp_stream_key_hint'), this.rtmpStreamKeyInput, this.onRtmpStreamKeyChange, false)
          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })
          this.SettingsTextField($r('app.string.rtmp_auth_username'), $r('app.string.field_optional'), this.rtmpUsernameInput, this.onRtmpUsernameChange, false)
          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })
          this.SettingsTextField($r('app.string.rtmp_auth_password'), $r('app.string.field_optional'), this.rtmpPasswordInput, this.onRtmpPasswordChange, !this.showPassword)
        }
        .width('100%')
        .borderRadius(20)
        .backgroundColor(this.surfaceColor)

        // Audio toggle
        Row() {
          Column() {
            Text($r('app.string.settings_mic_audio'))
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Medium)
            Text($r('app.string.settings_mic_audio_desc'))
              .fontSize(12)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ top: 4 })
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
          Toggle({ type: ToggleType.Switch, isOn: this.rtmpAudioEnabled })
            .onChange((v: boolean) => this.onRtmpAudioEnabledChange(v))
        }
        .width('100%')
        .borderRadius(20)
        .backgroundColor(this.surfaceColor)
        .padding(16)
        .margin({ top: 10 })

        // Auto stream toggle
        Row() {
          Column() {
            Text($r('app.string.settings_ask_rtmp'))
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Medium)
            Text($r('app.string.settings_ask_rtmp_desc'))
              .fontSize(12)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ top: 4 })
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
          Toggle({ type: ToggleType.Switch, isOn: this.rtmpAutoStreamEnabled })
            .onChange((v: boolean) => this.onRtmpAutoStreamChange(v))
        }
        .width('100%')
        .borderRadius(20)
        .backgroundColor(this.surfaceColor)
        .padding(16)
        .margin({ top: 10 })

        // App lock toggle
        Row() {
          Column() {
            Text($r('app.string.settings_app_lock'))
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Medium)
            Text($r('app.string.settings_app_lock_desc'))
              .fontSize(12)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ top: 4 })
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
          Toggle({ type: ToggleType.Switch, isOn: this.appLockEnabled })
            .onChange((v: boolean) => this.onAppLockChange(v))
        }
        .width('100%')
        .borderRadius(20)
        .backgroundColor(this.surfaceColor)
        .padding(16)
        .margin({ top: 10 })

        // RTMP settings manager
        Column() {
          Text($r('app.string.rtmp_settings_manager'))
            .fontSize(16)
            .fontColor(this.onSurfaceColor)
            .fontWeight(FontWeight.Medium)
          Text($r('app.string.rtmp_settings_manager_desc'))
            .fontSize(14)
            .fontColor(this.onSurfaceVariantColor)
            .margin({ top: 4 })
        }
        .width('100%')
        .borderRadius(18)
        .backgroundColor(this.surfaceColor)
        .padding(16)
        .margin({ top: 10 })
        .onClick(() => this.onManageRtmpSettings())

        // Clear recordings
        Text($r('app.string.clear_recordings'))
          .fontSize(16)
          .fontColor('#FFFFFF')
          .fontWeight(FontWeight.Bold)
          .width('100%')
          .textAlign(TextAlign.Center)
          .padding({ top: 16, bottom: 16 })
          .backgroundColor('#FF0000')
          .borderRadius(16)
          .margin({ top: 16 })
          .onClick(() => this.onClearRecordings())
      }
      .width('100%')
      .padding({ left: 20, right: 20, bottom: 120 })
      .backgroundColor(this.bgColor)
  }

  @Builder
  SettingsTextField(label: Resource, hint: Resource, value: string, onChange: (v: string) => void, password: boolean) {
    Column() {
      Text(label)
        .fontSize(13)
        .fontColor(this.onSurfaceVariantColor)
      if (password) {
        TextInput({ text: value, placeholder: hint })
          .fontSize(14)
          .fontColor(this.onSurfaceColor)
          .type(InputType.Password)
          .backgroundColor(Color.Transparent)
          .width('100%')
          .margin({ top: 6 })
          .onChange((v: string) => onChange(v))
      } else {
        TextInput({ text: value, placeholder: hint })
          .fontSize(14)
          .fontColor(this.onSurfaceColor)
          .backgroundColor(Color.Transparent)
          .width('100%')
          .margin({ top: 6 })
          .onChange((v: string) => onChange(v))
      }
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 10, bottom: 10 })
  }
}
