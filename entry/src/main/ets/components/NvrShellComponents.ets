import { CameraDevice } from '../model/CameraDevice';
import { RecordingFile, readableFileSize } from '../model/RecordingFile';
import { promptAction } from '@kit.ArkUI';

const XCOMPONENT_TYPE_SURFACE = 0;

interface FilterChipItem {
  label: string;
  value: string;
}

interface TabBarItem {
  label: string;
  icon: string;
  index: number;
}

@Component
export struct NvrMainTopBar {
  selectedTab: number = 0;
  accentColor: string = '#20B486';
  bgColor: string = '#F7F8FA';
  onBackgroundColor: string = '#1B1B1D';
  onPrimaryColor: string = '#FFFFFF';
  onEditClick: () => void = () => {};
  onMenuClick: () => void = () => {};
  onAddClick: () => void = () => {};

  build(): void {
    Column() {
      Row() {
        Text(this.selectedTab === 0 ? '编辑' : '')
          .fontSize(16)
          .fontColor(this.accentColor)
          .fontWeight(FontWeight.Medium)
          .onClick(() => this.onEditClick())
          .enabled(this.selectedTab === 0)

        Row({ space: 14 }) {
          Text('☰')
            .fontSize(22)
            .fontColor(this.accentColor)
            .onClick(() => this.onMenuClick())

          Stack() {
            Circle({ width: 30, height: 30 })
              .fill(this.accentColor)
            Text('+')
              .fontSize(20)
              .fontColor(this.onPrimaryColor)
              .fontWeight(FontWeight.Bold)
          }
          .width(30)
          .height(30)
          .onClick(() => this.onAddClick())
        }
        .justifyContent(FlexAlign.End)
        .layoutWeight(1)
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
      .alignItems(VerticalAlign.Center)
      .padding({ left: 18, right: 18, top: 10, bottom: 10 })

      Text(this.getTabTitle())
        .fontSize(28)
        .fontColor(this.onBackgroundColor)
        .fontWeight(FontWeight.Bold)
        .padding({ left: 18, bottom: 10 })
    }
    .width('100%')
    .backgroundColor(this.bgColor)
  }

  private getTabTitle(): string {
    switch (this.selectedTab) {
      case 0: return '摄像机';
      case 1: return '仪表板';
      case 2: return '活动';
      case 3: return '快照';
      default: return '设置';
    }
  }
}

@Component
export struct CameraHomeScreen {
  cameras: CameraDevice[] = [];
  snapshots: RecordingFile[] = [];
  selectedCameraId: string = '';
  playingCameraId: string = '';
  accentColor: string = '#20B486';
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  surfaceVariantColor: string = '#F0F1F5';
  onBackgroundColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  onPrimaryColor: string = '#FFFFFF';
  secondaryContainerColor: string = '#D6F3E8';
  errorColor: string = '#E53935';
  searchQuery: string = '';
  cameraFilter: string = 'all';
  onCameraClick: (device: CameraDevice) => void = () => {};
  onCameraLongPress: (device: CameraDevice) => void = () => {};
  onAddCamera: () => void = () => {};
  onSearchChange: (query: string) => void = () => {};
  onFilterChange: (filter: string) => void = () => {};
  @State gridColumns: number = 2;

  build(): void {
    if (this.cameras.length === 0) {
      Column() {
        Stack() {
          Circle({ width: 72, height: 72 })
            .fill(this.surfaceVariantColor)
          Text('◉')
            .fontSize(32)
            .fontColor(this.accentColor)
        }
        .width(72)
        .height(72)

        Text('暂无摄像机')
          .fontSize(22)
          .fontColor(this.onBackgroundColor)
          .fontWeight(FontWeight.Bold)
          .margin({ top: 16 })

        Text('添加 RTSP / ONVIF 设备，或先看演示')
          .fontSize(14)
          .fontColor(this.onSurfaceVariantColor)
          .margin({ top: 6 })

        Row({ space: 12 }) {
          Text('添加摄像机')
            .fontSize(16)
            .fontColor(this.onPrimaryColor)
            .fontWeight(FontWeight.Bold)
            .padding({ left: 22, right: 22, top: 12, bottom: 12 })
            .backgroundColor(this.accentColor)
            .borderRadius(18)
            .onClick(() => this.onAddCamera())

          Text('演示模式')
            .fontSize(16)
            .fontColor(this.onBackgroundColor)
            .fontWeight(FontWeight.Bold)
            .padding({ left: 22, right: 22, top: 12, bottom: 12 })
            .backgroundColor(this.surfaceVariantColor)
            .borderRadius(18)
        }
        .margin({ top: 24 })
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .padding({ left: 20, right: 20 })
      .backgroundColor(this.bgColor)
    } else {
      Column() {
        // Search bar
        Row() {
          TextInput({ text: this.searchQuery, placeholder: '搜索摄像机...' })
            .fontSize(14)
            .fontColor(this.onBackgroundColor)
            .backgroundColor(this.surfaceVariantColor)
            .borderRadius(12)
            .padding({ left: 12, right: 12, top: 8, bottom: 8 })
            .layoutWeight(1)
            .onChange((v: string) => this.onSearchChange(v))

          if (this.searchQuery !== '') {
            Text('✕')
              .fontSize(16)
              .fontColor(this.onSurfaceVariantColor)
              .margin({ left: 8 })
              .onClick(() => this.onSearchChange(''))
          }
        }
        .width('100%')
        .padding({ left: 18, right: 18, top: 4, bottom: 8 })

        // Filter chips
        Row() {
          ForEach([
            { label: '全部', value: 'all' },
            { label: '在线', value: 'online' },
            { label: '离线', value: 'offline' },
          ], (item: FilterChipItem) => {
            Text(item.label)
              .fontSize(12)
              .fontColor(this.cameraFilter === item.value ? this.onPrimaryColor : this.onSurfaceVariantColor)
              .fontWeight(this.cameraFilter === item.value ? FontWeight.Medium : FontWeight.Normal)
              .padding({ left: 14, right: 14, top: 6, bottom: 6 })
              .backgroundColor(this.cameraFilter === item.value ? this.accentColor : this.surfaceVariantColor)
              .borderRadius(999)
              .margin({ right: 8 })
              .onClick(() => this.onFilterChange(item.value))
          })
        }
        .width('100%')
        .padding({ left: 18, right: 18, bottom: 8 })

        Row() {
          Text(`在线 ${this.cameras.filter(c => c.isConnected).length} / ${this.cameras.length}`)
            .fontSize(14)
            .fontColor(this.onSurfaceVariantColor)
            .fontWeight(FontWeight.Medium)

          Row({ space: 2 }) {
            ForEach([1, 2, 3], (cols: number) => {
              Text(cols === 1 ? '▢' : cols === 2 ? '⊞' : '⊟')
                .fontSize(14)
                .fontColor(this.gridColumns === cols ? this.onBackgroundColor : this.onSurfaceVariantColor)
                .fontWeight(FontWeight.Bold)
                .padding({ left: 12, right: 12, top: 6, bottom: 6 })
                .backgroundColor(this.gridColumns === cols ? this.secondaryContainerColor : Color.Transparent)
                .borderRadius(999)
                .onClick(() => { this.gridColumns = cols; })
            })
          }
          .backgroundColor(this.surfaceVariantColor)
          .borderRadius(999)
          .padding(2)
        }
        .width('100%')
        .justifyContent(FlexAlign.SpaceBetween)
        .alignItems(VerticalAlign.Center)
        .padding({ left: 18, right: 16, top: 4, bottom: 10 })

        Grid() {
          ForEach(this.cameras, (device: CameraDevice) => {
            GridItem() {
              CameraGridCard({
                camera: device,
                isSelected: this.selectedCameraId === device.id,
                isPlaying: this.playingCameraId === device.id,
                accentColor: this.accentColor,
                surfaceVariantColor: this.surfaceVariantColor,
                onBackgroundColor: this.onBackgroundColor,
                onSurfaceVariantColor: this.onSurfaceVariantColor,
                secondaryContainerColor: this.secondaryContainerColor,
                errorColor: this.errorColor,
                onCardClick: () => this.onCameraClick(device),
                onLongPress: () => this.onCameraLongPress(device),
              })
            }
          })
        }
        .columnsTemplate(this.gridColumns === 1 ? '1fr' : this.gridColumns === 2 ? '1fr 1fr' : '1fr 1fr 1fr')
        .layoutWeight(1)
        .padding({ left: 16, right: 16 })
        .columnsGap(12)
        .rowsGap(this.gridColumns >= 3 ? 14 : 18)
      }
      .width('100%')
      .height('100%')
      .backgroundColor(this.bgColor)
    }
  }
}

@Component
export struct CameraGridCard {
  camera: CameraDevice = { id: '', name: '', rtspUrl: '', isRecording: false, isConnected: false };
  isSelected: boolean = false;
  isPlaying: boolean = false;
  accentColor: string = '#20B486';
  surfaceVariantColor: string = '#F0F1F5';
  onBackgroundColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  secondaryContainerColor: string = '#D6F3E8';
  errorColor: string = '#E53935';
  onCardClick: () => void = () => {};
  onLongPress: () => void = () => {};

  build(): void {
    Column() {
      Stack() {
        Column()
          .width('100%')
          .aspectRatio(1.55)
          .borderRadius(14)
          .linearGradient({
            direction: GradientDirection.Right,
            colors: [[this.surfaceVariantColor, 0.0], [this.isPlaying ? this.secondaryContainerColor : this.surfaceVariantColor, 1.0]]
          })

        Row() {
          Circle({ width: 6, height: 6 })
            .fill(this.getStatusDotColor())
          Text(this.getStatusLabel())
            .fontSize(10)
            .fontColor('#FFFFFFEB')
            .fontWeight(FontWeight.Bold)
        }
        .padding({ left: 8, right: 8, top: 4, bottom: 4 })
        .backgroundColor('#6B000000')
        .borderRadius(999)
        .align(Alignment.TopStart)
        .margin({ left: 8, top: 8 })

        if (this.isPlaying) {
          Text('REC')
            .fontSize(10)
            .fontColor('#FFFFFFEB')
            .fontWeight(FontWeight.Bold)
            .padding({ left: 8, right: 8, top: 4, bottom: 4 })
            .backgroundColor(this.errorColor)
            .borderRadius(999)
            .align(Alignment.TopEnd)
            .margin({ right: 8, top: 8 })
        }

        // Connection quality indicator
        if (this.camera.connectionQuality && this.camera.connectionQuality !== 'unknown') {
          Row() {
            ForEach([0, 1, 2], (idx: number) => {
              Column()
                .width(3)
                .height(idx === 0 ? 4 : idx === 1 ? 7 : 10)
                .backgroundColor(this.getQualityBarColor(idx))
                .borderRadius(1)
                .margin({ left: idx > 0 ? 1 : 0 })
            })
          }
          .align(Alignment.BottomEnd)
          .margin({ right: 8, bottom: 8 })
        }
      }
      .width('100%')
      .aspectRatio(1.55)
      .borderRadius(14)
      .clip(true)

      Row() {
        Text(this.camera.name || '未命名')
          .fontSize(16)
          .fontColor(this.onBackgroundColor)
          .fontWeight(FontWeight.Bold)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)

        Text('CAM')
          .fontSize(12)
          .fontColor(this.onSurfaceVariantColor)
          .padding({ left: 14, right: 14, top: 6, bottom: 6 })
          .backgroundColor(this.surfaceVariantColor)
          .borderRadius(999)
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
      .alignItems(VerticalAlign.Center)
      .margin({ top: 8 })
    }
    .width('100%')
    .onClick(() => this.onCardClick())
    .gesture(
      LongPressGesture()
        .onAction(() => this.onLongPress())
    )
  }

  private getStatusLabel(): string {
    if (this.isPlaying) return 'LIVE';
    if (this.camera.isConnected) return 'STANDBY';
    return 'OFFLINE';
  }

  private getStatusDotColor(): ResourceColor {
    if (this.isPlaying) return this.errorColor;
    if (this.camera.isConnected) return this.accentColor;
    return this.onSurfaceVariantColor;
  }

  private getQualityBarColor(idx: number): ResourceColor {
    const quality = this.camera.connectionQuality ?? 'unknown';
    if (quality === 'good') return this.accentColor;
    if (quality === 'fair') return idx < 2 ? '#FFA726' : '#4DFFFFFF';
    if (quality === 'poor') return idx === 0 ? this.errorColor : '#4DFFFFFF';
    return '#4DFFFFFF';
  }
}

@Component
export struct PlaybackFullscreenScreen {
  camera: CameraDevice = { id: '', name: '', rtspUrl: '', isRecording: false, isConnected: false };
  badge: string = 'CAM';
  isPlaying: boolean = false;
  isMuted: boolean = false;
  isVideoReady: boolean = false;
  isRecording: boolean = false;
  accentColor: string = '#20B486';
  onPrimaryColor: string = '#FFFFFF';
  errorColor: string = '#E53935';
  surfaceVariantColor: string = '#F0F1F5';
  surfaceId: string = '';
  onBindSurface: (id: string) => void = () => {};
  onBack: () => void = () => {};
  onTogglePlayPause: () => void = () => {};
  onMute: () => void = () => {};
  onSnapshot: () => void = () => {};
  onMore: () => void = () => {};
  onToggleRecord: () => void = () => {};
  onSwipeLeft: () => void = () => {};
  onSwipeRight: () => void = () => {};
  onPtzCommand: (command: string) => void = () => {};
  showPtzControls: boolean = false;
  @State controlsVisible: boolean = true;
  @State zoomScale: number = 1.0;
  @State showPtzPad: boolean = false;
  private controlsTimerId: number = -1;
  private xComponentController: XComponentController = new XComponentController();

  aboutToDisappear(): void {
    if (this.controlsTimerId !== -1) {
      clearTimeout(this.controlsTimerId);
    }
  }

  private resetControlsTimer(): void {
    if (this.controlsTimerId !== -1) {
      clearTimeout(this.controlsTimerId);
    }
    if (this.controlsVisible && this.isPlaying) {
      this.controlsTimerId = setTimeout(() => {
        this.controlsVisible = false;
      }, 4000);
    }
  }

  build(): void {
    Stack() {
      Column() {
        XComponent({
          id: 'nvrVideoSurface',
          type: XCOMPONENT_TYPE_SURFACE,
          libraryname: 'avplayer',
          controller: this.xComponentController,
        })
          .onLoad(() => {
            const surfaceId = this.xComponentController.getXComponentSurfaceId();
            this.onBindSurface(surfaceId);
          })
          .width('100%')
          .height('100%')
          .scale({ x: this.zoomScale, y: this.zoomScale })
      }
      .width('100%')
      .height('100%')
      .backgroundColor('#000000')
      .gesture(
        SwipeGesture({ speed: 0.3 })
          .onAction((event: GestureEvent) => {
            if (event.angle > -45 && event.angle < 45) {
              this.onSwipeLeft();
            } else if (event.angle > 135 || event.angle < -135) {
              this.onSwipeRight();
            }
          })
      )
      .gesture(
        PinchGesture({ fingers: 2 })
          .onActionEnd((event: GestureEvent) => {
            if (event.scale > 1.0) {
              this.zoomScale = Math.min(this.zoomScale * 1.3, 3.0);
            } else {
              this.zoomScale = Math.max(this.zoomScale / 1.3, 1.0);
            }
          })
      )
      .gesture(
        TapGesture({ count: 2 })
          .onAction(() => {
            if (this.zoomScale > 1.0) {
              this.zoomScale = 1.0;
            } else {
              this.zoomScale = 2.0;
            }
          })
      )

      if (!this.isVideoReady) {
        Column() {
          LoadingProgress()
            .width(40)
            .height(40)
            .color(this.accentColor)
          Text('正在加载画面…')
            .fontSize(14)
            .fontColor('#FFFFFF')
            .margin({ top: 12 })
          Text('高码率视频首次缓冲可能需要几秒')
            .fontSize(11)
            .fontColor('#BEBEC2')
            .margin({ top: 4 })
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor('#CC000000')
      }

      if (this.controlsVisible) {
        Column() {
          Row() {
            Text('‹')
              .fontSize(32)
              .fontColor('#FFFFFF')
              .onClick(() => this.onBack())

            Text(this.camera.name || this.badge)
              .fontSize(20)
              .fontColor('#FFFFFF')
              .fontWeight(FontWeight.Bold)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .layoutWeight(1)
              .margin({ left: 16 })

            Text(this.badge)
              .fontSize(12)
              .fontColor('#BEBEC2')
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          .padding({ left: 16, right: 16, top: 10, bottom: 10 })
          .linearGradient({
            direction: GradientDirection.Bottom,
            colors: [['#CC000000', 0.0], ['#00000000', 1.0]]
          })
        }
        .width('100%')
        .align(Alignment.Top)

        Row() {
          Row({ space: 14 }) {
            Text('📷')
              .fontSize(22)
              .fontColor('#FFFFFFB3')
              .width(44)
              .height(44)
              .textAlign(TextAlign.Center)
              .onClick(() => this.onSnapshot())

            Text(this.isRecording ? '●' : '○')
              .fontSize(22)
              .fontColor(this.errorColor)
              .width(44)
              .height(44)
              .textAlign(TextAlign.Center)
              .onClick(() => this.onToggleRecord())

            Text(this.isMuted ? '🔇' : '🔊')
              .fontSize(22)
              .fontColor('#FFFFFFB3')
              .width(44)
              .height(44)
              .textAlign(TextAlign.Center)
              .onClick(() => this.onMute())
          }
          .alignItems(VerticalAlign.Center)

          Text('⋮')
            .fontSize(22)
            .fontColor('#FFFFFF')
            .width(44)
            .height(44)
            .textAlign(TextAlign.Center)
            .border({ width: 2, color: '#FFFFFF', radius: 22 })
            .onClick(() => this.onMore())
        }
        .width('100%')
        .justifyContent(FlexAlign.SpaceBetween)
        .alignItems(VerticalAlign.Center)
        .padding({ left: 20, right: 20, top: 14, bottom: 14 })
        .linearGradient({
          direction: GradientDirection.Top,
          colors: [['#00000000', 0.0], ['#CC000000', 1.0]]
        })
        .align(Alignment.Bottom)

        Stack() {
          Circle({ width: 80, height: 80 })
            .fill(this.accentColor)
          Text(this.isPlaying ? '⏸' : '▶')
            .fontSize(this.isPlaying ? 28 : 32)
            .fontColor(this.onPrimaryColor)
        }
        .width(80)
        .height(80)
        .align(Alignment.Center)
        .onClick(() => this.onTogglePlayPause())

        // PTZ control pad
        if (this.showPtzPad || this.showPtzControls) {
          Row() {
            // PTZ directional pad
            Column() {
              Text('▲')
                .fontSize(24)
                .fontColor('#FFFFFFCC')
                .width(48)
                .height(48)
                .textAlign(TextAlign.Center)
                .onClick(() => this.onPtzCommand('up'))

              Row() {
                Text('◀')
                  .fontSize(24)
                  .fontColor('#FFFFFFCC')
                  .width(48)
                  .height(48)
                  .textAlign(TextAlign.Center)
                  .onClick(() => this.onPtzCommand('left'))

                Text('●')
                  .fontSize(20)
                  .fontColor('#FFFFFF80')
                  .width(48)
                  .height(48)
                  .textAlign(TextAlign.Center)
                  .onClick(() => this.onPtzCommand('home'))

                Text('▶')
                  .fontSize(24)
                  .fontColor('#FFFFFFCC')
                  .width(48)
                  .height(48)
                  .textAlign(TextAlign.Center)
                  .onClick(() => this.onPtzCommand('right'))
              }

              Text('▼')
                .fontSize(24)
                .fontColor('#FFFFFFCC')
                .width(48)
                .height(48)
                .textAlign(TextAlign.Center)
                .onClick(() => this.onPtzCommand('down'))
            }
            .alignItems(HorizontalAlign.Center)
            .backgroundColor('#33000000')
            .borderRadius(16)
            .padding(8)
          }
          .align(Alignment.BottomStart)
          .padding({ left: 20, bottom: 80 })
        }
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#000000')
    .onClick(() => {
      this.controlsVisible = !this.controlsVisible;
      if (this.controlsVisible) {
        this.resetControlsTimer();
      }
    })
  }

  aboutToAppear(): void {
    this.resetControlsTimer();
  }
}

@Component
export struct NvrBottomBar {
  selectedTab: number = 0;
  accentColor: string = '#20B486';
  surfaceColor: string = '#FFFFFF';
  onSurfaceVariantColor: string = '#6B6F76';
  onPrimaryColor: string = '#FFFFFF';
  onTabSelected: (index: number) => void = () => {};

  build(): void {
    Row() {
      ForEach([
        { label: '摄像机', icon: '📹', index: 0 },
        { label: '浏览', icon: '🧭', index: 1 },
        { label: '活动', icon: '📊', index: 2 },
        { label: '快照', icon: '📷', index: 3 },
        { label: '设置', icon: '⚙', index: 4 },
      ], (item: TabBarItem) => {
        Column() {
          Text(item.icon)
            .fontSize(22)
            .fontColor(this.selectedTab === item.index ? this.accentColor : this.onSurfaceVariantColor)
          Text(item.label)
            .fontSize(11)
            .fontColor(this.selectedTab === item.index ? this.accentColor : this.onSurfaceVariantColor)
            .fontWeight(this.selectedTab === item.index ? FontWeight.Bold : FontWeight.Medium)
            .margin({ top: 4 })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        .padding({ top: 6, bottom: 6 })
        .borderRadius(18)
        .onClick(() => this.onTabSelected(item.index))
      })
    }
    .width('100%')
    .justifyContent(FlexAlign.SpaceEvenly)
    .alignItems(VerticalAlign.Center)
    .backgroundColor(this.surfaceColor)
    .padding({ left: 6, right: 6, top: 8, bottom: 8 })
  }
}

@Component
export struct CameraActionOverlay {
  camera: CameraDevice = { id: '', name: '', rtspUrl: '', isRecording: false, isConnected: false };
  badge: string = 'CAM';
  accentColor: string = '#20B486';
  surfaceColor: string = '#FFFFFF';
  onSurfaceColor: string = '#1B1B1D';
  outlineVariantColor: string = '#E0E2E8';
  onDismiss: () => void = () => {};
  onEdit: () => void = () => {};
  onCopy: () => void = () => {};
  onDelete: () => void = () => {};

  build(): void {
    Stack() {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor('#6B000000')
        .onClick(() => this.onDismiss())

      Column() {
        Text(this.camera.name || '未命名')
          .fontSize(18)
          .fontColor(this.onSurfaceColor)
          .fontWeight(FontWeight.Bold)
          .padding({ top: 16, bottom: 8 })

        Column() {
          Row() {
            Text('编辑')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .layoutWeight(1)
            Text('⚙')
              .fontSize(20)
              .fontColor(this.onSurfaceColor)
          }
          .width('100%')
          .justifyContent(FlexAlign.SpaceBetween)
          .padding({ left: 20, right: 20, top: 16, bottom: 16 })
          .onClick(() => this.onEdit())

          Divider().color(this.outlineVariantColor)

          Row() {
            Text('复制')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .layoutWeight(1)
            Text('⊞')
              .fontSize(20)
              .fontColor(this.onSurfaceColor)
          }
          .width('100%')
          .justifyContent(FlexAlign.SpaceBetween)
          .padding({ left: 20, right: 20, top: 16, bottom: 16 })
          .onClick(() => this.onCopy())

          Divider().color(this.outlineVariantColor)

          Row() {
            Text('删除')
              .fontSize(16)
              .fontColor(this.onSurfaceColor)
              .layoutWeight(1)
            Text('🗑')
              .fontSize(20)
              .fontColor(this.onSurfaceColor)
          }
          .width('100%')
          .justifyContent(FlexAlign.SpaceBetween)
          .padding({ left: 20, right: 20, top: 16, bottom: 16 })
          .onClick(() => this.onDelete())
        }
        .width('72%')
        .borderRadius(18)
        .backgroundColor(this.surfaceColor)
        .margin({ top: 110 })
      }
      .align(Alignment.Top)
    }
    .width('100%')
    .height('100%')
  }
}
