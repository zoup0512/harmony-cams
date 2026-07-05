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
      } else if (this.flowStep === DeviceFlowStep.VendorPicker) {
        Text('✕')
          .fontSize(20)
          .fontColor(this.onSurfaceColor)
          .width(38)
          .height(38)
          .textAlign(TextAlign.Center)
          .borderRadius(19)
          .backgroundColor('#33888888')
          .onClick(() => this.onBack())
      } else if (this.flowStep === DeviceFlowStep.Form && this.isEditingExistingCamera) {
        Text('取消')
          .fontSize(16)
          .fontColor(this.errorColor)
          .onClick(() => this.onCancel())
      } else {
        Text('‹')
          .fontSize(24)
          .fontColor(this.onSurfaceColor)
          .width(38)
          .height(38)
          .textAlign(TextAlign.Center)
          .borderRadius(19)
          .backgroundColor('#33888888')
          .onClick(() => this.onBack())
      }

      Text(this.title)
        .fontSize(20)
        .fontColor(this.onSurfaceColor)
        .fontWeight(FontWeight.Medium)
        .textAlign(TextAlign.Center)
        .layoutWeight(1)

      if (this.flowStep === DeviceFlowStep.Landing || this.flowStep === DeviceFlowStep.Form) {
        Text('?')
          .fontSize(18)
          .fontColor(this.onSurfaceColor)
          .width(38)
          .height(38)
          .textAlign(TextAlign.Center)
          .borderRadius(19)
          .backgroundColor('#33888888')
      } else {
        Text('')
          .width(38)
      }
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
  manageVendor: string = '';
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
  onOpenVendorPicker: () => void = () => {};
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

  @State showAdvanced: boolean = false;

  build(): void {
    Column() {
      Scroll() {
        Column() {
          Text('手动连接')
            .fontSize(32)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.onSurfaceColor)
            .width('100%')
            .margin({ top: 6, bottom: 22 })

          // 手动连接 section
          Text('手动连接')
            .fontSize(15)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ bottom: 10 })

          Column() {
            Row() {
              Text('主机 / IP')
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .width(96)
              TextInput({ text: this.manageHost, placeholder: '主机' })
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .textAlign(TextAlign.End)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .onChange((v: string) => this.onManageHostChange(v))
              Text('⌕')
                .fontSize(20)
                .fontColor(this.accentColor)
                .margin({ left: 8 })
            }
            .width('100%')
            .padding({ top: 8, bottom: 8 })
          }
          .width('100%')
          .borderRadius(16)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18 })

          Text('输入摄像机的 IP 地址或主机名。')
            .fontSize(13)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ top: 8 })

          // 登录 section
          Text('登录')
            .fontSize(15)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ top: 24, bottom: 10 })

          Column() {
            Row() {
              Text('用户名')
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .width(96)
              TextInput({ text: this.manageUsername, placeholder: 'admin' })
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .textAlign(TextAlign.End)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .onChange((v: string) => this.onManageUsernameChange(v))
            }
            .width('100%')
            .padding({ top: 8, bottom: 8 })

            Divider().color(this.outlineVariantColor)

            Row() {
              Text('密码')
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .width(96)
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
                .margin({ left: 8 })
                .onClick(() => this.onTogglePasswordVisible())
            }
            .width('100%')
            .padding({ top: 8, bottom: 8 })
          }
          .width('100%')
          .borderRadius(16)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18 })

          Text('如果是匿名认证，请留空')
            .fontSize(13)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ top: 8 })

          // 高级 section
          Text('高级')
            .fontSize(15)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ top: 24, bottom: 10 })

          Column() {
            Row() {
              Text('品牌')
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .layoutWeight(1)
              Text(this.manageVendor.trim() !== '' ? this.manageVendor : '自动检测（ONVIF）')
                .fontSize(16)
                .fontColor(this.onSurfaceVariantColor)
              Text('›')
                .fontSize(20)
                .fontColor(this.onSurfaceVariantColor)
                .margin({ left: 8 })
            }
            .width('100%')
            .padding({ top: 16, bottom: 16 })
            .onClick(() => this.onOpenVendorPicker())

            Divider().color(this.outlineVariantColor)

            Row() {
              Text('高级设置')
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .layoutWeight(1)
              Text(this.showAdvanced ? '⌄' : '›')
                .fontSize(20)
                .fontColor(this.onSurfaceVariantColor)
            }
            .width('100%')
            .padding({ top: 16, bottom: 16 })
            .onClick(() => { this.showAdvanced = !this.showAdvanced; })

            if (this.showAdvanced) {
              Divider().color(this.outlineVariantColor)

              Row() {
                Text('方案')
                  .fontSize(16)
                  .fontColor(this.onSurfaceColor)
                  .width(96)
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
              .padding({ top: 12, bottom: 12 })

              Divider().color(this.outlineVariantColor)

              Row() {
                Text('端口')
                  .fontSize(16)
                  .fontColor(this.onSurfaceColor)
                  .width(96)
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
              .padding({ top: 8, bottom: 8 })

              Divider().color(this.outlineVariantColor)

              Row() {
                Text('路径')
                  .fontSize(16)
                  .fontColor(this.onSurfaceColor)
                  .width(96)
                TextInput({ text: this.manageStreamPath, placeholder: 'stream1' })
                  .fontSize(16)
                  .fontColor(this.onSurfaceColor)
                  .textAlign(TextAlign.End)
                  .backgroundColor(Color.Transparent)
                  .layoutWeight(1)
                  .onChange((v: string) => this.onManageStreamPathChange(v))
              }
              .width('100%')
              .padding({ top: 8, bottom: 8 })

              Divider().color(this.outlineVariantColor)

              Row() {
                Text('登录已启用')
                  .fontSize(16)
                  .fontColor(this.onSurfaceColor)
                  .layoutWeight(1)
                Toggle({ type: ToggleType.Switch, isOn: this.manageLoginEnabled })
                  .onChange((v: boolean) => this.onManageLoginEnabledChange(v))
              }
              .width('100%')
              .padding({ top: 10, bottom: 10 })

              if (this.getSelectedScheme() === PlaybackStreamType.HTTP) {
                Divider().color(this.outlineVariantColor)

                Row() {
                  Text('强制软件解码')
                    .fontSize(16)
                    .fontColor(this.onSurfaceColor)
                    .layoutWeight(1)
                  Toggle({ type: ToggleType.Switch, isOn: this.forceHttpSoftwareDecode })
                    .onChange((v: boolean) => this.onForceHttpSoftwareDecodeChange(v))
                }
                .width('100%')
                .padding({ top: 10, bottom: 10 })
              }
            }
          }
          .width('100%')
          .borderRadius(16)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18 })

          if (this.showAdvanced) {
            Text(`IPCams 将连接在 ${sanitizePlaybackUrl(this.getPreviewUrl())}`)
              .fontSize(13)
              .fontColor(this.onSurfaceVariantColor)
              .width('100%')
              .margin({ top: 8 })
          }
        }
        .width('100%')
        .padding({ top: 4, bottom: 20 })
      }
      .scrollBar(BarState.Off)
      .layoutWeight(1)

      Text('继续')
        .fontSize(18)
        .fontColor(this.onPrimaryColor)
        .fontWeight(FontWeight.Bold)
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: 16, bottom: 16 })
        .backgroundColor(this.accentColor)
        .borderRadius(28)
        .margin({ bottom: 18 })
        .onClick(() => this.onSave())
    }
    .width('100%')
    .layoutWeight(1)
    .backgroundColor(this.bgColor)
    .padding({ left: 18, right: 18 })
  }
}

@Component
export struct AddCameraVerificationScreen {
  result: VerificationPreview | null = null;
  forceHttpSoftwareDecode: boolean = false;
  manageName: string = '';
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  accentColor: string = '#20B486';
  onPrimaryColor: string = '#FFFFFF';
  primaryColor: string = '#20B486';
  onNameChange: (v: string) => void = () => {};
  onSave: () => void = () => {};
  @State isConnecting: boolean = true;
  private connectTimerId: number = -1;

  aboutToAppear(): void {
    this.connectTimerId = setTimeout(() => {
      this.isConnecting = false;
    }, 1500);
  }

  aboutToDisappear(): void {
    if (this.connectTimerId !== -1) {
      clearTimeout(this.connectTimerId);
    }
  }

  build(): void {
    Column() {
      Scroll() {
        Column() {
          Text('添加相机')
            .fontSize(32)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.onSurfaceColor)
            .width('100%')
            .margin({ top: 6, bottom: 22 })

          // 连接 section
          Text('连接')
            .fontSize(15)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ bottom: 10 })

          Row() {
            Text(this.isConnecting ? '正在连接...' : '已连接')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .layoutWeight(1)
            if (this.isConnecting) {
              LoadingProgress()
                .width(22)
                .height(22)
                .color(this.onSurfaceVariantColor)
            } else {
              Text('✔')
                .fontSize(18)
                .fontColor(this.accentColor)
            }
          }
          .width('100%')
          .borderRadius(16)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18, top: 16, bottom: 16 })

          // 主码流 section
          Text('主码流')
            .fontSize(15)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ top: 24, bottom: 10 })

          Row() {
            Text('主码流')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .layoutWeight(1)
            Text('●')
              .fontSize(16)
              .fontColor(this.isConnecting ? this.onSurfaceVariantColor : this.accentColor)
          }
          .width('100%')
          .borderRadius(16)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18, top: 16, bottom: 16 })

          // 相机信息 section
          Text('相机信息')
            .fontSize(15)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ top: 24, bottom: 10 })

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
                .onChange((v: string) => this.onNameChange(v))
            }
            .width('100%')
            .padding({ top: 8, bottom: 8 })

            Divider().color(this.onSurfaceVariantColor).opacity(0.2)

            Row() {
              Column() {
                Text('图标')
                  .fontSize(16)
                  .fontColor(this.onSurfaceVariantColor)
                Text('IPCams')
                  .fontSize(14)
                  .fontColor(this.onSurfaceVariantColor)
                  .margin({ top: 2 })
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)

              Text('📷')
                .fontSize(18)
              Text('›')
                .fontSize(20)
                .fontColor(this.onSurfaceVariantColor)
                .margin({ left: 8 })
            }
            .width('100%')
            .padding({ top: 12, bottom: 12 })
          }
          .width('100%')
          .borderRadius(16)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18 })

          Text('用名字和图标来个性化你的相机。')
            .fontSize(13)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ top: 8 })

          Text(`URL： ${sanitizePlaybackUrl(this.result?.primaryUrl ?? '')}`)
            .fontSize(13)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .margin({ top: 16 })
        }
        .width('100%')
        .padding({ top: 4, bottom: 20 })
      }
      .scrollBar(BarState.Off)
      .layoutWeight(1)

      Text('添加相机')
        .fontSize(18)
        .fontColor(this.onPrimaryColor)
        .fontWeight(FontWeight.Bold)
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: 16, bottom: 16 })
        .backgroundColor(this.accentColor)
        .borderRadius(28)
        .margin({ bottom: 18 })
        .onClick(() => this.onSave())
    }
    .width('100%')
    .layoutWeight(1)
    .backgroundColor(this.bgColor)
    .padding({ left: 18, right: 18 })
  }
}

const HOT_MANUFACTURERS: string[] = [
  'Amcrest', 'Axis', 'D-Link', 'Dahua', 'Eufy', 'EZVIZ', 'Foscam', 'Hikvision',
];

const ALL_MANUFACTURERS: string[] = [
  '7Links', 'ABUS', 'Acromedia', 'Amcrest', 'Annke', 'Axis', 'Bosch', 'Canon',
  'D-Link', 'Dahua', 'EZVIZ', 'Eufy', 'Foscam', 'GeoVision', 'Hanwha', 'Hikvision',
  'Honeywell', 'IPCams', 'Imou', 'Instar', 'Logitech', 'Lorex', 'Mobotix', 'Netatmo',
  'Panasonic', 'Reolink', 'Sony', 'Sricam', 'Swann', 'Synology', 'TP-Link', 'Tapo',
  'Trendnet', 'TVT', 'Ubiquiti', 'Uniview', 'Vivotek', 'Wansview', 'Wyze', 'Xiaomi',
  'YI', 'Yale', 'ZOSI', 'Zmodo',
];

@Component
export struct ManufacturerPickerScreen {
  selectedVendor: string = '';
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  accentColor: string = '#20B486';
  outlineVariantColor: string = '#E0E2E8';
  onSelectVendor: (v: string) => void = () => {};
  @State searchQuery: string = '';

  private filterBrands(brands: string[]): string[] {
    const q = this.searchQuery.trim().toLowerCase();
    if (q === '') return brands;
    return brands.filter((b: string) => b.toLowerCase().includes(q));
  }

  @Builder
  BrandRow(brand: string, isLast: boolean) {
    Column() {
      Row() {
        Text(brand)
          .fontSize(16)
          .fontColor(this.onSurfaceColor)
          .layoutWeight(1)
        if (this.selectedVendor === brand) {
          Text('✔')
            .fontSize(16)
            .fontColor(this.accentColor)
            .margin({ right: 8 })
        }
        Text('›')
          .fontSize(20)
          .fontColor(this.onSurfaceVariantColor)
      }
      .width('100%')
      .padding({ top: 16, bottom: 16 })
      .onClick(() => this.onSelectVendor(brand))

      if (!isLast) {
        Divider().color(this.outlineVariantColor)
      }
    }
    .width('100%')
  }

  @Builder
  BrandSection(title: string, brands: string[]) {
    Text(title)
      .fontSize(15)
      .fontColor(this.onSurfaceVariantColor)
      .width('100%')
      .margin({ top: 24, bottom: 10 })

    Column() {
      ForEach(brands, (brand: string, index: number) => {
        this.BrandRow(brand, index === brands.length - 1)
      }, (brand: string) => brand)
    }
    .width('100%')
    .borderRadius(16)
    .backgroundColor(this.surfaceColor)
    .padding({ left: 18, right: 18 })
  }

  build(): void {
    Column() {
      Row() {
        Text('⌕')
          .fontSize(18)
          .fontColor(this.onSurfaceVariantColor)
          .margin({ right: 6 })
        TextInput({ text: this.searchQuery, placeholder: '搜索制造商' })
          .fontSize(16)
          .fontColor(this.onSurfaceColor)
          .backgroundColor(Color.Transparent)
          .layoutWeight(1)
          .onChange((v: string) => { this.searchQuery = v; })
      }
      .width('100%')
      .borderRadius(22)
      .backgroundColor(this.surfaceColor)
      .padding({ left: 14, right: 14 })
      .alignItems(VerticalAlign.Center)

      Scroll() {
        Column() {
          Column() {
            Row() {
              Text('自动检测（ONVIF）')
                .fontSize(16)
                .fontColor(this.onSurfaceColor)
                .layoutWeight(1)
              if (this.selectedVendor.trim() === '') {
                Text('✔')
                  .fontSize(16)
                  .fontColor(this.accentColor)
              }
            }
            .width('100%')
            .padding({ top: 16, bottom: 16 })
            .onClick(() => this.onSelectVendor(''))
          }
          .width('100%')
          .borderRadius(16)
          .backgroundColor(this.surfaceColor)
          .padding({ left: 18, right: 18 })
          .margin({ top: 24 })

          if (this.filterBrands(HOT_MANUFACTURERS).length > 0) {
            this.BrandSection('热门品牌', this.filterBrands(HOT_MANUFACTURERS))
          }

          if (this.filterBrands(ALL_MANUFACTURERS).length > 0) {
            this.BrandSection('所有品牌', this.filterBrands(ALL_MANUFACTURERS))
          }

          if (this.searchQuery.trim() !== ''
            && this.filterBrands(HOT_MANUFACTURERS).length === 0
            && this.filterBrands(ALL_MANUFACTURERS).length === 0) {
            Text('未找到匹配的制造商')
              .fontSize(15)
              .fontColor(this.onSurfaceVariantColor)
              .width('100%')
              .textAlign(TextAlign.Center)
              .margin({ top: 40 })
          }
        }
        .width('100%')
        .padding({ bottom: 24 })
      }
      .scrollBar(BarState.Off)
      .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.bgColor)
    .padding({ left: 18, right: 18, top: 4 })
  }
}
