import { fileIo } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { RecordingFile, createRecordingFile } from '../model/RecordingFile';

export class StorageManager {
  private context: common.Context;

  constructor(context: common.Context) {
    this.context = context;
  }

  getBaseDirectoryPath(): string {
    return this.context.filesDir;
  }

  getRecordingDirectoryPath(): string {
    const dir = `${this.getBaseDirectoryPath()}/recordings`;
    try {
      fileIo.accessSync(dir);
    } catch {
      fileIo.mkdirSync(dir, true);
    }
    return dir;
  }

  getSnapshotDirectoryPath(rootPath?: string): string {
    const baseDir = rootPath && rootPath.trim() !== '' ? rootPath : this.getBaseDirectoryPath();
    const dir = `${baseDir}/snapshot`;
    try {
      fileIo.accessSync(dir);
    } catch {
      fileIo.mkdirSync(dir, true);
    }
    return dir;
  }

  getAllRecordings(): RecordingFile[] {
    const result: RecordingFile[] = [];
    const dir = this.getRecordingDirectoryPath();
    try {
      const files = fileIo.listFileSync(dir);
      for (const fileName of files) {
        const filePath = `${dir}/${fileName}`;
        try {
          const stat = fileIo.statSync(filePath);
          if (stat.isDirectory()) continue;
          result.push(createRecordingFile(
            '',
            fileName,
            filePath,
            stat.size,
          ));
        } catch {
          // skip
        }
      }
    } catch {
      // dir not exist
    }
    return result;
  }

  getAllSnapshots(rootPath?: string): RecordingFile[] {
    const result: RecordingFile[] = [];
    const dir = this.getSnapshotDirectoryPath(rootPath);
    this.walkSnapshots(dir, result);
    result.sort((a, b) => b.fileSize - a.fileSize);
    return result;
  }

  private walkSnapshots(dirPath: string, result: RecordingFile[]) {
    try {
      const files = fileIo.listFileSync(dirPath);
      for (const fileName of files) {
        const filePath = `${dirPath}/${fileName}`;
        try {
          const stat = fileIo.statSync(filePath);
          if (stat.isDirectory()) {
            this.walkSnapshots(filePath, result);
          } else {
            const ext = fileName.split('.').pop()?.toLowerCase() ?? '';
            if (['png', 'jpg', 'jpeg', 'webp'].includes(ext)) {
              const cameraName = fileName.includes('_')
                ? fileName.substring(0, fileName.lastIndexOf('_'))
                : fileName.replace(/\.[^.]+$/, '');
              result.push(createRecordingFile(
                '',
                fileName,
                filePath,
                stat.size,
                null,
                null,
                '',
                cameraName,
              ));
            }
          }
        } catch {
          // skip
        }
      }
    } catch {
      // dir not exist
    }
  }

  deleteFile(filePath: string): boolean {
    try {
      fileIo.unlinkSync(filePath);
      return true;
    } catch {
      return false;
    }
  }
}
