import { RecordingFile, readableFileSize } from '../model/RecordingFile';

interface ThemeOptionItem {
  value: string;
  label: string;
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
  @State selectedStreamTool: string = 'rtsp';
  @State isStreaming: boolean = false;
  @State useFrontCamera: boolean = false;
  @State statusText: string = '未启动';

  build(): void {
    Column() {
        Row({ space: 10 }) {
          Text('RTSP 服务器')
              .fontSize(14)
              .fontColor(this.selectedStreamTool === 'rtsp' ? this.onPrimaryColor : this.onSurfaceVariantColor)
              .fontWeight(this.selectedStreamTool === 'rtsp' ? FontWeight.Medium : FontWeight.Normal)
              .layoutWeight(1)
              .textAlign(TextAlign.Center)
              .padding({ top: 10, bottom: 10 })
              .backgroundColor(this.selectedStreamTool === 'rtsp' ? this.accentColor : this.surfaceVariantColor)
              .borderRadius(12)
              .onClick(() => { this.selectedStreamTool = 'rtsp'; })

            Text('RTMP 推流')
              .fontSize(14)
              .fontColor(this.selectedStreamTool === 'rtmp' ? this.onPrimaryColor : this.onSurfaceVariantColor)
              .fontWeight(this.selectedStreamTool === 'rtmp' ? FontWeight.Medium : FontWeight.Normal)
              .layoutWeight(1)
              .textAlign(TextAlign.Center)
              .padding({ top: 10, bottom: 10 })
              .backgroundColor(this.selectedStreamTool === 'rtmp' ? this.accentColor : this.surfaceVariantColor)
              .borderRadius(12)
              .onClick(() => { this.selectedStreamTool = 'rtmp'; })
          }
          .width('100%')

          // Phone camera streaming card
          Column() {
            Row() {
              Column() {
                Text(this.selectedStreamTool === 'rtmp' ? 'RTMP 推流到服务器' : '手机摄像头 RTSP 推流')
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

              Text(this.isStreaming ? '运行中' : '待启动')
                .fontSize(12)
                .fontColor(this.isStreaming ? this.onPrimaryColor : this.onSurfaceVariantColor)
                .padding({ left: 12, right: 12, top: 6, bottom: 6 })
                .backgroundColor(this.isStreaming ? this.accentColor : this.surfaceVariantColor)
                .borderRadius(999)
            }
            .width('100%')
            .justifyContent(FlexAlign.SpaceBetween)

            Stack() {
              Column()
                .width('100%')
                .height('100%')
                .justifyContent(FlexAlign.Center)
                .alignItems(HorizontalAlign.Center)
              Text(this.isStreaming ? '🔴 推流中' : '📷 预览区')
                .fontSize(16)
                .fontColor('#FFFFFF80')
            }
            .width('100%')
            .aspectRatio(4 / 3)
            .borderRadius(16)
            .backgroundColor('#000000')
            .margin({ top: 12 })

            Text(this.selectedStreamTool === 'rtmp'
              ? '目标地址：请在设置中配置 RTMP 服务器'
              : '推流地址：rtsp://127.0.0.1:8554/live')
              .fontSize(12)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ top: 12 })

            Row({ space: 10 }) {
              Button(this.useFrontCamera ? '切到后置' : '切到前置')
                .layoutWeight(1)
                .onClick(() => {
                  this.useFrontCamera = !this.useFrontCamera;
                  this.statusText = this.useFrontCamera ? '已切到前置摄像头' : '已切到后置摄像头';
                })
              Button(this.isStreaming ? '停止推流' : '开始推流')
                .layoutWeight(1)
                .onClick(() => {
                  if (this.isStreaming) {
                    this.isStreaming = false;
                    this.statusText = '已停止';
                  } else {
                    this.isStreaming = true;
                    this.statusText = '推流中';
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

          Text('活动记录')
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)
            .fontWeight(FontWeight.Medium)
            .margin({ top: 14, bottom: 4 })

          if (this.recordings.length === 0) {
            Text('暂无活动记录')
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

                Text('查看')
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
  snapshots: RecordingFile[] = [];
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  accentColor: string = '#20B486';
  onOpenSnapshot: (file: RecordingFile) => void = () => {};
  onDeleteSnapshot: (file: RecordingFile) => void = () => {};
  onShareSnapshot: (file: RecordingFile) => void = () => {};

  build(): void {
    if (this.snapshots.length === 0) {
      Column() {
        Text('暂无快照')
          .fontSize(16)
          .fontColor(this.onSurfaceVariantColor)
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .backgroundColor(this.bgColor)
    } else {
      Column() {
          ForEach(this.snapshots, (snapshot: RecordingFile) => {
            Column() {
              Image(snapshot.filePath)
                .width('100%')
                .aspectRatio(1.15)
                .borderRadius(12)
                .objectFit(ImageFit.Cover)
                .backgroundColor(this.surfaceColor)

              Text(snapshot.fileName)
                .fontSize(14)
                .fontColor(this.onSurfaceColor)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .margin({ top: 10 })

              Text(readableFileSize(snapshot.fileSize))
                .fontSize(12)
                .fontColor(this.onSurfaceVariantColor)
                .margin({ top: 4 })

              Row() {
                Text('查看')
                  .fontSize(12)
                  .fontColor(this.accentColor)
                  .fontWeight(FontWeight.Medium)
                  .layoutWeight(1)
                Text('📤')
                  .fontSize(16)
                  .margin({ right: 8 })
                  .onClick(() => this.onShareSnapshot(snapshot))
                Text('🗑')
                  .fontSize(16)
                  .onClick(() => this.onDeleteSnapshot(snapshot))
              }
              .width('100%')
              .margin({ top: 8 })
            }
            .layoutWeight(1)
            .borderRadius(18)
            .backgroundColor(this.surfaceColor)
            .padding(12)
            .margin({ bottom: 16 })
            .onClick(() => this.onOpenSnapshot(snapshot))
            .gesture(
              LongPressGesture()
                .onAction(() => this.onDeleteSnapshot(snapshot))
            )
          })
        }
        .width('100%')
        .padding({ left: 16, top: 12, right: 16, bottom: 100 })
        .backgroundColor(this.bgColor)
    }
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
  @State bgPlaybackEnabled: boolean = false;
  appLockEnabled: boolean = false;
  onAppLockChange: (enabled: boolean) => void = () => {};

  build(): void {
    Column() {
        // Appearance
        Text('✨ 外观')
          .fontSize(16)
          .fontColor(this.onSurfaceVariantColor)
          .fontWeight(FontWeight.Medium)
          .margin({ top: 16, bottom: 10 })

        // Theme selector
        Row() {
          Text('主题')
            .fontSize(16)
            .fontColor(this.onSurfaceColor)
            .fontWeight(FontWeight.Bold)
            .width(46)

          Row({ space: 3 }) {
            ForEach([
              { value: 'system', label: '系统' },
              { value: 'light', label: '明亮' },
              { value: 'dark', label: '暗' },
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
        Text('设备选项')
          .fontSize(16)
          .fontColor(this.onSurfaceVariantColor)
          .fontWeight(FontWeight.Medium)
          .margin({ top: 16, bottom: 10 })

        Column() {
          // Storage path
          Column() {
            Text('存储路径')
              .fontSize(13)
              .fontColor(this.onSurfaceVariantColor)
            Row() {
              Text(this.storagePathInput || '点击选择存储目录')
                .fontSize(14)
                .fontColor(this.storagePathInput ? this.onSurfaceColor : this.onSurfaceVariantColor)
                .layoutWeight(1)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              Text('选择')
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
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 10, bottom: 10 })

          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })

          // Recording quality
          Column() {
            Text('录制质量')
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
            Text('录制时长')
              .fontSize(13)
              .fontColor(this.onSurfaceVariantColor)
            TextInput({ text: this.recordingDurationInput, placeholder: '分钟数，如 30' })
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
            Text('允许后台播放')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Medium)
            Text('关闭时，应用切到后台会自动停止画面拉流；开启后将在后台继续播放')
              .fontSize(12)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ top: 4 })
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
          Toggle({ type: ToggleType.Switch, isOn: this.bgPlaybackEnabled })
            .onChange((v: boolean) => { this.bgPlaybackEnabled = v; })
        }
        .width('100%')
        .borderRadius(20)
        .backgroundColor(this.surfaceColor)
        .padding(16)
        .margin({ top: 10 })

        // RTMP
        Text('RTMP 推流')
          .fontSize(16)
          .fontColor(this.onSurfaceVariantColor)
          .fontWeight(FontWeight.Medium)
          .margin({ top: 16, bottom: 10 })

        Column() {
          this.SettingsTextField('服务器地址', 'rtmp://your-server/live', this.rtmpUrlInput, this.onRtmpUrlChange, false)
          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })
          this.SettingsTextField('推流密钥', '与服务器地址拼接为完整推流路径', this.rtmpStreamKeyInput, this.onRtmpStreamKeyChange, false)
          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })
          this.SettingsTextField('鉴权用户名', '可留空', this.rtmpUsernameInput, this.onRtmpUsernameChange, false)
          Divider().color(this.outlineVariantColor).margin({ left: 16, right: 16 })
          this.SettingsTextField('鉴权密码', '可留空', this.rtmpPasswordInput, this.onRtmpPasswordChange, !this.showPassword)
        }
        .width('100%')
        .borderRadius(20)
        .backgroundColor(this.surfaceColor)

        // Audio toggle
        Row() {
          Column() {
            Text('采集麦克风音频')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Medium)
            Text('推流时同时携带本机音频轨道')
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
            Text('询问RTMP设置')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Medium)
            Text('从保存的列表中选择RTMP设置而不是默认设置')
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
            Text('应用锁')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Medium)
            Text('使用 PIN 码保护应用，切到后台后需要解锁')
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
          Text('RTMP设置管理器')
            .fontSize(16)
            .fontColor(this.onSurfaceColor)
            .fontWeight(FontWeight.Medium)
          Text('管理或重新使用以前的RTMP推流地址和视频密钥')
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
        Text('清理录像')
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
  SettingsTextField(label: string, hint: string, value: string, onChange: (v: string) => void, password: boolean) {
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
