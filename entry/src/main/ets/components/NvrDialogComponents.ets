import { RecordingFile } from '../model/RecordingFile';
import { fileIo } from '@kit.CoreFileKit';

@Component
export struct RecordingPreviewDialog {
  dialogFile: RecordingFile = {
    id: '', fileName: '', filePath: '', fileSize: 0,
    startTime: null, endTime: null, cameraId: '', cameraName: ''
  };
  accentColor: string = '#20B486';
  onDismiss: () => void = () => {};

  build(): void {
    Column() {
      Text(this.dialogFile.fileName)
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor('#1B1B1D')
        .padding({ top: 24, left: 24, right: 24 })

      Text(this.dialogFile.filePath)
        .fontSize(14)
        .fontColor('#6B6F76')
        .padding({ left: 24, right: 24, top: 8 })

      if (this.isImageFile()) {
        Image(this.dialogFile.filePath)
          .width('85%')
          .height(260)
          .objectFit(ImageFit.Contain)
          .margin({ top: 16 })
      }

      Text('关闭')
        .fontSize(16)
        .fontColor(this.accentColor)
        .fontWeight(FontWeight.Medium)
        .padding({ top: 24, bottom: 24, left: 24, right: 24 })
        .onClick(() => this.onDismiss())
    }
    .width('85%')
    .borderRadius(20)
    .backgroundColor('#FFFFFF')
    .alignItems(HorizontalAlign.Center)
  }

  private isImageFile(): boolean {
    const ext = this.dialogFile.filePath.split('.').pop()?.toLowerCase() ?? '';
    return ['png', 'jpg', 'jpeg', 'webp'].includes(ext);
  }
}

@Component
export struct DeleteSnapshotConfirmDialog {
  fileName: string = '';
  accentColor: string = '#20B486';
  errorColor: string = '#E53935';
  onDismiss: () => void = () => {};
  onConfirmDelete: () => void = () => {};

  build(): void {
    Column() {
      Text('删除快照')
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor('#1B1B1D')
        .padding({ top: 24, left: 24, right: 24 })

      Text(this.fileName)
        .fontSize(14)
        .fontColor('#6B6F76')
        .padding({ left: 24, right: 24, top: 8 })

      Row() {
        Text('取消')
          .fontSize(16)
          .fontColor('#6B6F76')
          .layoutWeight(1)
          .textAlign(TextAlign.Center)
          .padding({ top: 16, bottom: 16 })
          .onClick(() => this.onDismiss())

        Text('删除')
          .fontSize(16)
          .fontColor(this.errorColor)
          .fontWeight(FontWeight.Bold)
          .layoutWeight(1)
          .textAlign(TextAlign.Center)
          .padding({ top: 16, bottom: 16 })
          .onClick(() => this.onConfirmDelete())
      }
      .width('100%')
      .margin({ top: 24 })
    }
    .width('80%')
    .borderRadius(20)
    .backgroundColor('#FFFFFF')
  }
}
