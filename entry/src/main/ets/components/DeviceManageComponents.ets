import { DeviceFlowStep, VerificationPreview, PlaybackStreamType, sanitizePlaybackUrl, buildPlaybackUrl, defaultPlaybackPort, playbackStreamTypeFromScheme } from '../utils/PlaybackModels';

@Component
export struct DeviceManageTopBar {
  title: string = '';
  flowStep: DeviceFlowStep = DeviceFlowStep.Landing;
  isEditingExistingCamera: boolean = false;
  accentColor: string = '#20B486';
  bgColor: string = '#F7F8FA';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  errorColor: string = '#E53935';
  onCancel: () => void = () => {};
  onBack: () => void = () => {};

  build(): void {
    Row() {
      if (this.flowStep === DeviceFlowStep.Landing) {
        Text('✕')
          .fontSize(22)
          .fontColor(this.onSurfaceVariantColor)
          .onClick(() => this.onCancel())
      } else if (this.flowStep === DeviceFlowStep.Form) {
        Text(this.isEditingExistingCamera ? '取消' : '‹ 添加相机')
          .fontSize(16)
          .fontColor(this.isEditingExistingCamera ? this.errorColor : this.accentColor)
          .onClick(() => this.isEditingExistingCamera ? this.onCancel() : this.onBack())
      } else {
        Text('取消')
          .fontSize(16)
          .fontColor(this.errorColor)
          .onClick(() => this.onCancel())
      }

      Text(this.title)
        .fontSize(20)
        .fontColor(this.onSurfaceColor)
        .fontWeight(FontWeight.Medium)
        .textAlign(TextAlign.Center)
        .layoutWeight(1)

      Text(this.flowStep === DeviceFlowStep.Landing ? '?' : '')
        .fontSize(20)
        .fontColor(this.accentColor)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding({ left: 20, right: 20, top: 16, bottom: 16 })
    .backgroundColor(this.bgColor)
  }
}

@Component
export struct AddCameraLandingScreen {
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  accentColor: string = '#20B486';
  secondaryContainerColor: string = '#D6F3E8';
  onAddLocalCamera: () => void = () => {};

  build(): void {
    Column() {
        Text('网络搜索')
          .fontSize(16)
          .fontColor(this.onSurfaceVariantColor)
          .padding({ top: 26, bottom: 12 })

        Column() {
          Stack() {
            Circle({ width: 56, height: 56 })
              .fill(this.secondaryContainerColor)
            Text('⌕')
              .fontSize(28)
              .fontColor(this.accentColor)
          }
          .width(56)
          .height(56)

          Text('正在搜索网络...')
            .fontSize(22)
            .fontColor(this.onSurfaceColor)
            .fontWeight(FontWeight.Bold)
            .margin({ top: 14 })

          Text('搜索并自动连接网络上的摄像头。没有看到您要找的东西？进行深度扫描')
            .fontSize(14)
            .fontColor(this.onSurfaceVariantColor)
            .textAlign(TextAlign.Center)
            .margin({ top: 8 })
        }
        .width('100%')
        .borderRadius(18)
        .backgroundColor(this.surfaceColor)
        .padding({ left: 18, right: 18, top: 20, bottom: 20 })
        .alignItems(HorizontalAlign.Center)

        Column() {
          Column() {
            Stack() {
              Circle({ width: 54, height: 54 })
                .fill(this.secondaryContainerColor)
              Text('+')
                .fontSize(28)
                .fontColor(this.accentColor)
                .fontWeight(FontWeight.Bold)
            }
            .width(54)
            .height(54)
            Text('添加本地摄像机')
              .fontSize(22)
              .fontColor(this.onSurfaceColor)
              .fontWeight(FontWeight.Bold)
              .margin({ top: 14 })
            Text('通过 IP 地址或域名添加本地网络摄像机')
              .fontSize(16)
              .fontColor(this.onSurfaceVariantColor)
              .textAlign(TextAlign.Center)
              .margin({ top: 8 })
          }
          .width('100%')
          .alignItems(HorizontalAlign.Center)
          .padding({ left: 20, right: 20, top: 18, bottom: 18 })
          .onClick(() => this.onAddLocalCamera())
        }
        .width('100%')
        .borderRadius(18)
        .backgroundColor(this.surfaceColor)
        .margin({ top: 20 })
      }
      .width('100%')
      .padding({ left: 18, right: 18 })
      .backgroundColor(this.bgColor)
    }
  }

@Component
export struct AddCameraFormScreen {
  manageName: string = '';
  manageStreamScheme: string = 'rtsp';
  manageHost: string = '';
  managePort: string = '';
  manageStreamPath: string = 'stream1';
  manageLoginEnabled: boolean = true;
  manageUsername: string = '';
  managePassword: string = '';
  managePasswordVisible: boolean = false;
  forceHttpSoftwareDecode: boolean = false;
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  accentColor: string = '#20B486';
  onPrimaryColor: string = '#FFFFFF';
  outlineVariantColor: string = '#E0E2E8';
  onManageNameChange: (v: string) => void = () => {};
  onManageStreamSchemeChange: (v: string) => void = () => {};
  onManageHostChange: (v: string) => void = () => {};
  onManagePortChange: (v: string) => void = () => {};
  onManageStreamPathChange: (v: string) => void = () => {};
  onManageLoginEnabledChange: (v: boolean) => void = () => {};
  onManageUsernameChange: (v: string) => void = () => {};
  onManagePasswordChange: (v: string) => void = () => {};
  onTogglePasswordVisible: () => void = () => {};
  onForceHttpSoftwareDecodeChange: (v: boolean) => void = () => {};
  onSave: () => void = () => {};

  private getSelectedScheme(): PlaybackStreamType {
    const parsed = playbackStreamTypeFromScheme(this.manageStreamScheme);
    if (parsed === PlaybackStreamType.RTMP) return PlaybackStreamType.HTTP;
    return parsed ?? PlaybackStreamType.RTSP;
  }

  private getPreviewUrl(): string {
    const scheme = this.getSelectedScheme();
    return buildPlaybackUrl(
      scheme,
      this.manageHost.trim(),
      this.managePort.trim() || defaultPlaybackPort(scheme),
      this.manageStreamPath.trim() || 'stream1',
      this.manageLoginEnabled,
      this.manageUsername.trim(),
      this.managePassword,
    );
  }

  build(): void {
    Column() {
      Scroll() {
        Column() {
          // Name card
          Column() {
            Row() {
              Text('命名')
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .width(96)
              TextInput({ text: this.manageName, placeholder: '命名' })
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .textAlign(TextAlign.End)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .onChange((v: string) => this.onManageNameChange(v))
            }
            .width('100%')
            .padding({ top: 15, bottom: 15 })

            Divider().color(this.outlineVariantColor)

            Row() {
              Text('图标')
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .width(96)
              Text('IPCams')
                .fontSize(16)
                .fontColor(this.onSurfaceVariantColor)
                .textAlign(TextAlign.End)
                .layoutWeight(1)
              Text('◉')
                .fontSize(20)
                .fontColor(this.onSurfaceVariantColor)
                .margin({ left: 10 })
            }
            .width('100%')
            .padding({ top: 15, bottom: 15 })
          }
          .width('100%')
          .borderRadius(18)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18 })

          Text('URL')
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)
            .margin({ top: 18 })

          // URL card
          Column() {
            Row() {
              Text('方案')
                .fontSize(18)
                .fontColor(this.onSurfaceColor)
                .width(98)
              Row({ space: 8 }) {
                Text('RTSP')
                  .fontSize(14)
                  .fontColor(this.getSelectedScheme() === PlaybackStreamType.RTSP ? this.onPrimaryColor : this.onSurfaceVariantColor)
                  .fontWeight(this.getSelectedScheme() === PlaybackStreamType.RTSP ? FontWeight.Medium : FontWeight.Normal)
                  .layoutWeight(1)
                  .textAlign(TextAlign.Center)
                  .padding({ top: 10, bottom: 10 })
                  .backgroundColor(this.getSelectedScheme() === PlaybackStreamType.RTSP ? this.accentColor : this.outlineVariantColor)
                  .borderRadius(12)
                  .onClick(() => this.onManageStreamSchemeChange(PlaybackStreamType.RTSP))

                Text('HTTP')
                  .fontSize(14)
                  .fontColor(this.getSelectedScheme() === PlaybackStreamType.HTTP ? this.onPrimaryColor : this.onSurfaceVariantColor)
                  .fontWeight(this.getSelectedScheme() === PlaybackStreamType.HTTP ? FontWeight.Medium : FontWeight.Normal)
                  .layoutWeight(1)
                  .textAlign(TextAlign.Center)
                  .padding({ top: 10, bottom: 10 })
                  .backgroundColor(this.getSelectedScheme() === PlaybackStreamType.HTTP ? this.accentColor : this.outlineVariantColor)
                  .borderRadius(12)
                  .onClick(() => this.onManageStreamSchemeChange(PlaybackStreamType.HTTP))
              }
              .layoutWeight(1)
            }
            .width('100%')
            .padding({ top: 15, bottom: 15 })

            Divider().color(this.outlineVariantColor)

            Row() {
              Text('主机 / IP / 域名')
                .fontSize(18)
                .fontColor(this.onSurfaceColor)
                .width(98)
              TextInput({ text: this.manageHost, placeholder: 'cam.example.com' })
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .textAlign(TextAlign.End)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .onChange((v: string) => this.onManageHostChange(v))
              Text('⌕')
                .fontSize(20)
                .fontColor(this.accentColor)
                .margin({ left: 10 })
            }
            .width('100%')
            .padding({ top: 15, bottom: 15 })

            Divider().color(this.outlineVariantColor)

            Row() {
              Text('端口')
                .fontSize(18)
                .fontColor(this.onSurfaceColor)
                .width(98)
              TextInput({ text: this.managePort, placeholder: defaultPlaybackPort(this.getSelectedScheme()) })
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .textAlign(TextAlign.End)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .type(InputType.Number)
                .onChange((v: string) => this.onManagePortChange(v))
            }
            .width('100%')
            .padding({ top: 15, bottom: 15 })

            Divider().color(this.outlineVariantColor)

            Row() {
              Text('路径')
                .fontSize(18)
                .fontColor(this.onSurfaceColor)
                .width(98)
              TextInput({ text: this.manageStreamPath, placeholder: 'stream1' })
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .textAlign(TextAlign.End)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .onChange((v: string) => this.onManageStreamPathChange(v))
            }
            .width('100%')
            .padding({ top: 15, bottom: 15 })
          }
          .width('100%')
          .borderRadius(18)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18 })

          Text(`IPCams 将连接在 ${sanitizePlaybackUrl(this.getPreviewUrl())}`)
            .fontSize(14)
            .fontColor(this.onSurfaceVariantColor)
            .margin({ top: 18 })

          Text('登录')
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)
            .margin({ top: 18 })

          // Login card
          Column() {
            Row() {
              Text('已启用')
                .fontSize(18)
                .fontColor(this.onSurfaceColor)
                .layoutWeight(1)
              Toggle({ type: ToggleType.Switch, isOn: this.manageLoginEnabled })
                .onChange((v: boolean) => this.onManageLoginEnabledChange(v))
            }
            .width('100%')
            .padding({ top: 10, bottom: 10 })

            Divider().color(this.outlineVariantColor)

            Row() {
              Text('用户名')
                .fontSize(18)
                .fontColor(this.onSurfaceColor)
                .width(98)
              TextInput({ text: this.manageUsername, placeholder: 'admin' })
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .textAlign(TextAlign.End)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .onChange((v: string) => this.onManageUsernameChange(v))
            }
            .width('100%')
            .padding({ top: 15, bottom: 15 })

            Divider().color(this.outlineVariantColor)

            Row() {
              Text('密码')
                .fontSize(18)
                .fontColor(this.onSurfaceColor)
                .width(98)
              if (this.managePasswordVisible) {
                TextInput({ text: this.managePassword, placeholder: '密码' })
                  .fontSize(16)
                  .fontColor(this.onSurfaceColor)
                  .textAlign(TextAlign.End)
                  .backgroundColor(Color.Transparent)
                  .layoutWeight(1)
                  .onChange((v: string) => this.onManagePasswordChange(v))
              } else {
                TextInput({ text: this.managePassword, placeholder: '密码' })
                  .fontSize(16)
                  .fontColor(this.onSurfaceColor)
                  .textAlign(TextAlign.End)
                  .backgroundColor(Color.Transparent)
                  .layoutWeight(1)
                  .type(InputType.Password)
                  .onChange((v: string) => this.onManagePasswordChange(v))
              }
              Text('👁')
                .fontSize(18)
                .fontColor(this.accentColor)
                .margin({ left: 10 })
                .onClick(() => this.onTogglePasswordVisible())
            }
            .width('100%')
            .padding({ top: 15, bottom: 15 })
          }
          .width('100%')
          .borderRadius(18)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18 })

          if (this.getSelectedScheme() === PlaybackStreamType.HTTP) {
            Text('HTTP 兼容性')
              .fontSize(16)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ top: 18 })

            Column() {
              Row() {
                Text('强制软件解码')
                  .fontSize(18)
                  .fontColor(this.onSurfaceColor)
                  .layoutWeight(1)
                Toggle({ type: ToggleType.Switch, isOn: this.forceHttpSoftwareDecode })
                  .onChange((v: boolean) => this.onForceHttpSoftwareDecodeChange(v))
              }
              .width('100%')
              .padding({ top: 10, bottom: 10 })
            }
            .width('100%')
            .borderRadius(18)
            .backgroundColor(this.surfaceColor)
            .padding({ left: 18, right: 18 })
          }
        }
        .width('100%')
        .padding({ top: 18, bottom: 20 })
      }
      .scrollBar(BarState.Off)
      .layoutWeight(1)

      Text('保存')
        .fontSize(20)
        .fontColor(this.onPrimaryColor)
        .fontWeight(FontWeight.Bold)
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: 16, bottom: 16 })
        .backgroundColor(this.accentColor)
        .borderRadius(16)
        .margin({ bottom: 18 })
        .onClick(() => this.onSave())
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.bgColor)
    .padding({ left: 18, right: 18 })
  }
}

@Component
export struct AddCameraVerificationScreen {
  result: VerificationPreview | null = null;
  forceHttpSoftwareDecode: boolean = false;
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  accentColor: string = '#20B486';
  onPrimaryColor: string = '#FFFFFF';
  primaryColor: string = '#20B486';
  onSave: () => void = () => {};

  build(): void {
    Column() {
      Scroll() {
        Column({ space: 18 }) {
          Text('主码流')
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)

          this.VerificationCard('主码流', [
            `协议： ${(this.result?.streamScheme ?? 'rtsp').toUpperCase()}`,
            `分辨率： ${this.result?.resolution ?? '2560x1440'}`,
            `编解码器： ${this.result?.codec ?? 'hevc'}`,
            `软件解码： ${this.forceHttpSoftwareDecode ? '强制启用' : '未启用'}`,
            `URL： ${sanitizePlaybackUrl(this.result?.primaryUrl ?? '')}`,
          ], true)

          Text('次码流')
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)

          this.VerificationCard('次码流', ['未配置'], false)

          Text('快照')
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)

          this.VerificationCard('快照', ['未配置'], false)
        }
        .width('100%')
        .padding({ top: 18, bottom: 20 })
      }
      .scrollBar(BarState.Off)
      .layoutWeight(1)

      Text('保存')
        .fontSize(20)
        .fontColor(this.onPrimaryColor)
        .fontWeight(FontWeight.Bold)
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: 16, bottom: 16 })
        .backgroundColor(this.accentColor)
        .borderRadius(16)
        .margin({ bottom: 18 })
        .onClick(() => this.onSave())
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.bgColor)
    .padding({ left: 18, right: 18 })
  }

  @Builder
  VerificationCard(title: string, subtitleLines: string[], checked: boolean) {
    Row() {
      Column() {
        Text(title)
          .fontSize(22)
          .fontColor(this.onSurfaceColor)
        ForEach(subtitleLines, (line: string) => {
          Text(line)
            .fontSize(16)
            .fontColor(this.onSurfaceVariantColor)
            .margin({ top: 6 })
        })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      Text(checked ? '✔' : '●')
        .fontSize(22)
        .fontColor(checked ? this.primaryColor : this.onSurfaceVariantColor)
        .margin({ left: 14 })
    }
    .width('100%')
    .borderRadius(18)
    .backgroundColor(this.surfaceColor)
    .padding({ left: 18, right: 18, top: 18, bottom: 18 })
    .alignItems(VerticalAlign.Top)
  }
}
