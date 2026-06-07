import { NativeModules, Platform } from 'react-native';

const { AppLockerModule } = NativeModules;

export interface InstalledApp {
  packageName: string;
  name: string;
  iconResId: number;
}

const isAndroid = Platform.OS === 'android';

export const AppLocker = {
  async getInstalledApps(): Promise<InstalledApp[]> {
    if (!isAndroid) return [];
    return AppLockerModule.getInstalledApps();
  },

  async lockApp(packageName: string): Promise<void> {
    if (!isAndroid) return;
    return AppLockerModule.lockApp(packageName);
  },

  async unlockApp(packageName: string): Promise<void> {
    if (!isAndroid) return;
    return AppLockerModule.unlockApp(packageName);
  },

  async getLockedApps(): Promise<string[]> {
    if (!isAndroid) return [];
    return AppLockerModule.getLockedApps();
  },

  async setPin(pin: string): Promise<boolean> {
    if (!isAndroid) return false;
    return AppLockerModule.setPin(pin);
  },

  async verifyPin(pin: string): Promise<boolean> {
    if (!isAndroid) return false;
    return AppLockerModule.verifyPin(pin);
  },

  async hasPin(): Promise<boolean> {
    if (!isAndroid) return false;
    return AppLockerModule.hasPin();
  },

  async isAccessibilityServiceEnabled(): Promise<boolean> {
    if (!isAndroid) return false;
    return AppLockerModule.isAccessibilityServiceEnabled();
  },

  openAccessibilitySettings(): void {
    if (!isAndroid) return;
    AppLockerModule.openAccessibilitySettings();
  },

  openAppOverlaySettings(): void {
    if (!isAndroid) return;
    AppLockerModule.openAppOverlaySettings();
  },
};
