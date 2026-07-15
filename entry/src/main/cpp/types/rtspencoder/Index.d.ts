export const createEncoder: (width: number, height: number, bitrate: number, framerate: number, callback: (frame: EncodedFrame) => void) => string;
export const startEncoder: () => void;
export const stopEncoder: () => void;
export const releaseEncoder: () => void;
export const setMotionDetection: (enabled: boolean, threshold: number) => void;
export const setMotionCallback: (callback: (event: MotionEvent) => void) => void;

export interface EncodedFrame {
  data: ArrayBuffer;
  pts: number;
  flags: number;
  isKeyFrame: boolean;
}

export interface MotionEvent {
  timestamp: number;
  frameSize: number;
  avgSize: number;
  ratio: number;
}
