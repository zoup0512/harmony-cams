import { promptAction } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { PrefStore } from '../utils/PrefStore';
import {
  loadBrowseFavorites, addBrowseFavorite, removeBrowseFavorite,
  loadBrowseNetworkSources, addBrowseNetworkSource, removeBrowseNetworkSource,
  applyAuthToUrl,
} from '../utils/BrowsePersistence';
import { BrowseFavorite, BrowseNetworkSource, BrowseSourceKind, DiscoveredDevice } from '../model/BrowseModels';
import { NetworkDiscoveryService } from '../utils/NetworkDiscoveryService';

@Component
export struct BrowseScreen {
  accentColor: string = '#20B486';
  bgColor: string = '#F7F8FA';
  surfaceColor: string = '#FFFFFF';
  surfaceVariantColor: string = '#F0F1F5';
  onSurfaceColor: string = '#1B1B1D';
  onSurfaceVariantColor: string = '#6B6F76';
  onPrimaryColor: string = '#FFFFFF';
  onPlayUrl: (title: string, url: string) => void = () => {};
  @State titleInput: string = '';
  @State urlInput: string = '';
  @State userInput: string = '';
  @State passwordInput: string = '';
  @State favorites: BrowseFavorite[] = [];
  @State networkSources: BrowseNetworkSource[] = [];
  @State isScanning: boolean = false;
  @State discoveredDevices: DiscoveredDevice[] = [];
  private prefs: PrefStore | null = null;

  aboutToAppear(): void {
    const context = this.getUIContext().getHostContext() as common.Context;
    this.prefs = new PrefStore(context);
    this.prefs.init().then(() => this.loadData());
  }

  private async loadData(): Promise<void> {
    if (!this.prefs) return;
    this.favorites = await loadBrowseFavorites(this.prefs);
    this.networkSources = await loadBrowseNetworkSources(this.prefs);
  }

  build(): void {
    Column() {
        // Top bar
        Row() {
          Text('🎥')
            .fontSize(28)
            .margin({ right: 8 })
          Text('VLC')
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.onSurfaceColor)
          Blank()
          if (this.isScanning) {
            LoadingProgress()
              .width(20)
              .height(20)
              .color(this.accentColor)
          } else {
            Text('🔄')
              .fontSize(22)
              .fontColor(this.accentColor)
              .onClick(() => this.startScan())
          }
          Text('⋮')
            .fontSize(22)
            .fontColor(this.onSurfaceVariantColor)
            .margin({ left: 12 })
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
        .backgroundColor(this.surfaceColor)
        .padding({ left: 16, right: 16, top: 12, bottom: 12 })

        // Favorites section
        Text('收藏')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.accentColor)
          .width('100%')
          .padding({ left: 16, top: 12, bottom: 12 })

        if (this.favorites.length === 0) {
          Text('暂无收藏')
            .fontSize(14)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .textAlign(TextAlign.Center)
            .padding(32)
        } else {
          ForEach(this.favorites, (fav: BrowseFavorite) => {
            Row() {
              Text(fav.source === BrowseSourceKind.LOCAL_FILE ? '📁' : '🌐')
                .fontSize(28)
                .margin({ right: 12 })
              Column() {
                Text(fav.title)
                  .fontSize(14)
                  .fontColor(this.onSurfaceColor)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                Text(fav.source === BrowseSourceKind.LOCAL_FILE ? '本地文件' : '网络源')
                  .fontSize(12)
                  .fontColor(this.onSurfaceVariantColor)
                  .margin({ top: 4 })
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)
              Text('✕')
                .fontSize(16)
                .fontColor(this.onSurfaceVariantColor)
                .onClick(() => this.removeFavorite(fav.id))
            }
            .width('100%')
            .borderRadius(12)
            .backgroundColor(this.surfaceColor)
            .padding({ left: 16, right: 16, top: 14, bottom: 14 })
            .margin({ left: 16, right: 16, bottom: 8 })
            .onClick(() => this.onPlayUrl(fav.title, fav.url))
          })
        }

        // Storage section
        Text('存储设备')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.accentColor)
          .width('100%')
          .padding({ left: 16, top: 12, bottom: 12 })

        Text('未找到视频文件')
          .fontSize(14)
          .fontColor(this.onSurfaceVariantColor)
          .width('100%')
          .textAlign(TextAlign.Center)
          .padding(32)

        // Local network section
        Text('本地网络')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.accentColor)
          .width('100%')
          .padding({ left: 16, top: 12, bottom: 12 })

        // Discovered devices
        if (this.discoveredDevices.length > 0) {
          Text(`已发现设备 (${this.discoveredDevices.length})`)
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .padding({ left: 16, top: 8, bottom: 8 })

          ForEach(this.discoveredDevices, (dev: DiscoveredDevice) => {
            Row() {
              Column() {
                Text(dev.name)
                  .fontSize(14)
                  .fontColor(this.onSurfaceColor)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                Text(dev.host || dev.url)
                  .fontSize(12)
                  .fontColor(this.onSurfaceVariantColor)
                  .maxLines(1)
                  .margin({ top: 4 })
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)

              Text('▶')
                .fontSize(18)
                .fontColor(this.accentColor)
                .margin({ left: 10 })
            }
            .width('100%')
            .borderRadius(12)
            .backgroundColor(this.surfaceColor)
            .padding({ left: 16, right: 16, top: 14, bottom: 14 })
            .margin({ left: 16, right: 16, bottom: 8 })
            .onClick(() => this.onPlayUrl(dev.name, dev.url))
          })
        }

        // Add network source form
        Column() {
          Text('添加网络源')
            .fontSize(16)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.onSurfaceColor)

          TextInput({ text: this.titleInput, placeholder: '名称（可选）' })
            .fontSize(14)
            .fontColor(this.onSurfaceColor)
            .backgroundColor(this.surfaceVariantColor)
            .borderRadius(8)
            .padding({ left: 12, right: 12, top: 10, bottom: 10 })
            .margin({ top: 12 })
            .onChange((v: string) => { this.titleInput = v; })

          TextInput({ text: this.urlInput, placeholder: 'rtsp://, http://, rtmp://, smb:// ...' })
            .fontSize(14)
            .fontColor(this.onSurfaceColor)
            .backgroundColor(this.surfaceVariantColor)
            .borderRadius(8)
            .padding({ left: 12, right: 12, top: 10, bottom: 10 })
            .margin({ top: 12 })
            .onChange((v: string) => { this.urlInput = v; })

          Row() {
            TextInput({ text: this.userInput, placeholder: '用户名(可选)' })
              .fontSize(14)
              .fontColor(this.onSurfaceColor)
              .backgroundColor(this.surfaceVariantColor)
              .borderRadius(8)
              .padding({ left: 12, right: 12, top: 10, bottom: 10 })
              .layoutWeight(1)
              .onChange((v: string) => { this.userInput = v; })

            TextInput({ text: this.passwordInput, placeholder: '密码(可选)' })
              .fontSize(14)
              .fontColor(this.onSurfaceColor)
              .backgroundColor(this.surfaceVariantColor)
              .borderRadius(8)
              .padding({ left: 12, right: 12, top: 10, bottom: 10 })
              .layoutWeight(1)
              .margin({ left: 8 })
              .onChange((v: string) => { this.passwordInput = v; })
          }
          .width('100%')
          .margin({ top: 12 })

          Button('保存')
            .width('100%')
            .margin({ top: 12 })
            .onClick(() => this.addNetworkSource())

          Text('提示：请手动输入 URL。')
            .fontSize(12)
            .fontColor(this.onSurfaceVariantColor)
            .margin({ top: 12 })
        }
        .width('100%')
        .borderRadius(8)
        .backgroundColor(this.surfaceColor)
        .padding(16)
        .margin({ left: 16, right: 16 })

        // Network sources list
        if (this.networkSources.length === 0) {
          Text('尚未添加网络源')
            .fontSize(14)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .textAlign(TextAlign.Center)
            .padding(32)
        } else {
          Text('已保存的网络源')
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.onSurfaceVariantColor)
            .width('100%')
            .padding({ left: 16, top: 16, bottom: 8 })

          ForEach(this.networkSources, (src: BrowseNetworkSource) => {
            Row() {
              Column() {
                Text(src.title)
                  .fontSize(14)
                  .fontColor(this.onSurfaceColor)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                Text(src.url)
                  .fontSize(12)
                  .fontColor(this.onSurfaceVariantColor)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                  .margin({ top: 4 })
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)

              Text('▶')
                .fontSize(18)
                .fontColor(this.accentColor)
                .margin({ left: 10 })

              Text('✕')
                .fontSize(16)
                .fontColor(this.onSurfaceVariantColor)
                .margin({ left: 10 })
                .onClick(() => this.removeNetworkSource(src.id))
            }
            .width('100%')
            .borderRadius(12)
            .backgroundColor(this.surfaceColor)
            .padding({ left: 16, right: 16, top: 14, bottom: 14 })
            .margin({ left: 16, right: 16, bottom: 8 })
            .onClick(() => {
              const url = applyAuthToUrl(src.url, src.username, src.password);
              this.onPlayUrl(src.title, url);
            })
          })
        }
      }
      .width('100%')
      .padding({ bottom: 100 })
      .backgroundColor(this.bgColor)
    }

  private async addNetworkSource(): Promise<void> {
    if (this.urlInput.trim() === '') return;
    if (!this.prefs) return;
    const src: BrowseNetworkSource = {
      id: this.generateId(),
      title: this.titleInput.trim() || this.urlInput.trim(),
      url: this.urlInput.trim(),
      username: this.userInput.trim(),
      password: this.passwordInput,
      addedAtMs: Date.now(),
    };
    this.networkSources = await addBrowseNetworkSource(this.prefs, src);
    this.titleInput = '';
    this.urlInput = '';
    this.userInput = '';
    this.passwordInput = '';
    promptAction.showToast({ message: '已添加网络源' });
  }

  private async removeNetworkSource(id: string): Promise<void> {
    if (!this.prefs) return;
    this.networkSources = await removeBrowseNetworkSource(this.prefs, id);
  }

  private async removeFavorite(id: string): Promise<void> {
    if (!this.prefs) return;
    this.favorites = await removeBrowseFavorite(this.prefs, id);
  }

  private startScan(): void {
    if (this.isScanning) return;
    this.isScanning = true;
    const context = this.getUIContext().getHostContext() as common.Context;
    const service = new NetworkDiscoveryService(context);
    service.discoverAll().then((devices: DiscoveredDevice[]) => {
      this.discoveredDevices = devices;
      this.isScanning = false;
      if (devices.length === 0) {
        promptAction.showToast({ message: '未发现网络设备' });
      } else {
        promptAction.showToast({ message: `发现 ${devices.length} 个设备` });
      }
    }).catch(() => {
      this.isScanning = false;
      promptAction.showToast({ message: '扫描失败' });
    });
  }

  private generateId(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }
}
