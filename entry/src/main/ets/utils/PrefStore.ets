import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';

const PREFS_NAME = 'com.razor.cams_preferences';

export class PrefStore {
  private prefs: preferences.Preferences | null = null;
  private context: common.Context;

  constructor(context: common.Context) {
    this.context = context;
  }

  async init(): Promise<void> {
    const options: preferences.Options = {
      name: PREFS_NAME,
    };
    this.prefs = await preferences.getPreferences(this.context, options);
  }

  async getString(key: string, defaultValue: string = ''): Promise<string> {
    if (!this.prefs) await this.init();
    if (!this.prefs) return defaultValue;
    const val = await this.prefs.get(key, defaultValue);
    return val as string;
  }

  async getBoolean(key: string, defaultValue: boolean = false): Promise<boolean> {
    if (!this.prefs) await this.init();
    if (!this.prefs) return defaultValue;
    const val = await this.prefs.get(key, defaultValue);
    return val as boolean;
  }

  async putString(key: string, value: string): Promise<void> {
    if (!this.prefs) await this.init();
    if (!this.prefs) return;
    await this.prefs.put(key, value);
    await this.prefs.flush();
  }

  async putBoolean(key: string, value: boolean): Promise<void> {
    if (!this.prefs) await this.init();
    if (!this.prefs) return;
    await this.prefs.put(key, value);
    await this.prefs.flush();
  }

  async remove(key: string): Promise<void> {
    if (!this.prefs) await this.init();
    if (!this.prefs) return;
    await this.prefs.delete(key);
    await this.prefs.flush();
  }
}
