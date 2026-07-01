export interface CameraDevice {
  id: string;
  name: string;
  rtspUrl: string;
  isRecording: boolean;
  isConnected: boolean;
  group?: string;
  sortOrder?: number;
  lastSeen?: number;
  connectionQuality?: 'good' | 'fair' | 'poor' | 'unknown';
  coverImagePath?: string;
}

export function createCameraDevice(
  id: string = '',
  name: string = '',
  rtspUrl: string = '',
  isRecording: boolean = false,
  isConnected: boolean = false,
  group: string = '',
  sortOrder: number = 0,
): CameraDevice {
  return { id, name, rtspUrl, isRecording, isConnected, group, sortOrder, lastSeen: 0, connectionQuality: 'unknown' };
}
