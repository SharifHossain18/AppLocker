import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  Switch,
  StyleSheet,
  TextInput,
  Alert,
  TouchableOpacity,
  ActivityIndicator,
  AppState,
} from 'react-native';
import { AppLocker, InstalledApp } from '../native/AppLocker';

enum Screen {
  MAIN,
  SET_PIN,
  VERIFY_PIN,
}

export default function HomeScreen() {
  const [screen, setScreen] = useState<Screen>(Screen.MAIN);
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [lockedApps, setLockedApps] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [enteredPin, setEnteredPin] = useState('');
  const [hasPin, setHasPin] = useState(false);
  const [serviceEnabled, setServiceEnabled] = useState(false);

  const loadData = useCallback(async () => {
    try {
      const [installed, locked, serviceOn, pinExists] = await Promise.all([
        AppLocker.getInstalledApps(),
        AppLocker.getLockedApps(),
        AppLocker.isAccessibilityServiceEnabled(),
        AppLocker.hasPin(),
      ]);
      setApps(installed);
      setLockedApps(new Set(locked));
      setServiceEnabled(serviceOn);
      setHasPin(pinExists);
      if (pinExists) {
        setScreen(Screen.VERIFY_PIN);
      } else {
        setScreen(Screen.MAIN);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        loadData();
      }
    });
    return () => sub.remove();
  }, [loadData]);

  const toggleLock = async (pkg: string, value: boolean) => {
    try {
      if (value) {
        await AppLocker.lockApp(pkg);
        setLockedApps(prev => new Set(prev).add(pkg));
      } else {
        await AppLocker.unlockApp(pkg);
        setLockedApps(prev => {
          const next = new Set(prev);
          next.delete(pkg);
          return next;
        });
      }
    } catch (e) {
      console.error(e);
    }
  };

  const savePin = async () => {
    if (pin.length < 4) {
      Alert.alert('Error', 'PIN must be at least 4 digits');
      return;
    }
    if (pin !== confirmPin) {
      Alert.alert('Error', 'PINs do not match');
      return;
    }
    try {
      await AppLocker.setPin(pin);
      setHasPin(true);
      setPin('');
      setConfirmPin('');
      setScreen(Screen.MAIN);
      Alert.alert('Success', 'PIN set successfully');
    } catch {
      Alert.alert('Error', 'Failed to set PIN');
    }
  };

  const checkPin = async () => {
    if (enteredPin.length < 4) {
      Alert.alert('Error', 'PIN must be at least 4 digits');
      return;
    }
    try {
      const success = await AppLocker.verifyPin(enteredPin);
      if (success) {
        setScreen(Screen.MAIN);
        setEnteredPin('');
      } else {
        Alert.alert('Error', 'Incorrect PIN');
        setEnteredPin('');
      }
    } catch {
      Alert.alert('Error', 'Failed to verify PIN');
    }
  };

  const renderAppItem = ({ item }: { item: InstalledApp }) => {
    const isLocked = lockedApps.has(item.packageName);
    return (
      <View style={styles.appItem}>
        <View style={styles.appInfo}>
          <Text style={styles.appName} numberOfLines={1}>
            {item.name}
          </Text>
          <Text style={styles.appPackage} numberOfLines={1}>
            {item.packageName}
          </Text>
        </View>
        <Switch
          value={isLocked}
          onValueChange={val => toggleLock(item.packageName, val)}
          trackColor={{ false: '#333', true: '#10b981' }}
          thumbColor={isLocked ? '#fff' : '#666'}
        />
      </View>
    );
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" color="#10b981" />
      </View>
    );
  }

  if (screen === Screen.VERIFY_PIN) {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>Unlock AppLocker</Text>
        <TextInput
          style={styles.pinInput}
          placeholder="Enter PIN"
          placeholderTextColor="#666"
          keyboardType="number-pad"
          secureTextEntry
          maxLength={6}
          value={enteredPin}
          onChangeText={setEnteredPin}
        />
        <TouchableOpacity style={styles.saveBtn} onPress={checkPin}>
          <Text style={styles.saveBtnText}>Verify & Unlock</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (screen === Screen.SET_PIN) {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>Set Lock PIN</Text>
        <TextInput
          style={styles.pinInput}
          placeholder="Enter PIN"
          placeholderTextColor="#666"
          keyboardType="number-pad"
          secureTextEntry
          maxLength={6}
          value={pin}
          onChangeText={setPin}
        />
        <TextInput
          style={styles.pinInput}
          placeholder="Confirm PIN"
          placeholderTextColor="#666"
          keyboardType="number-pad"
          secureTextEntry
          maxLength={6}
          value={confirmPin}
          onChangeText={setConfirmPin}
        />
        <TouchableOpacity style={styles.saveBtn} onPress={savePin}>
          <Text style={styles.saveBtnText}>Save PIN</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.cancelBtn}
          onPress={() => setScreen(Screen.MAIN)}>
          <Text style={styles.cancelBtnText}>Cancel</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>AppLocker</Text>

      <View style={styles.statusCard}>
        <View style={styles.statusRow}>
          <Text style={styles.statusLabel}>Accessibility Service</Text>
          <TouchableOpacity
            style={[
              styles.statusBadge,
              serviceEnabled ? styles.statusOn : styles.statusOff,
            ]}
            onPress={() => {
              if (!serviceEnabled) {
                AppLocker.openAccessibilitySettings();
              }
            }}>
            <Text style={styles.statusText}>
              {serviceEnabled ? 'ON' : 'OFF — Tap to enable'}
            </Text>
          </TouchableOpacity>
        </View>
        <View style={styles.statusRow}>
          <Text style={styles.statusLabel}>Lock PIN</Text>
          <TouchableOpacity
            style={[styles.statusBadge, hasPin ? styles.statusOn : styles.statusOff]}
            onPress={() => setScreen(Screen.SET_PIN)}>
            <Text style={styles.statusText}>
              {hasPin ? 'Set' : 'Tap to set PIN'}
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>
          Apps ({apps.length})
        </Text>
        <Text style={styles.sectionSubtitle}>
          {lockedApps.size} locked
        </Text>
      </View>

      <FlatList
        data={apps}
        keyExtractor={item => item.packageName}
        renderItem={renderAppItem}
        style={styles.list}
        contentContainerStyle={styles.listContent}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f172a',
    paddingTop: 60,
    paddingHorizontal: 16,
  },
  center: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#f8fafc',
    marginBottom: 20,
  },
  statusCard: {
    backgroundColor: '#1e293b',
    borderRadius: 16,
    padding: 16,
    marginBottom: 20,
    gap: 12,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  statusLabel: {
    color: '#94a3b8',
    fontSize: 14,
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  statusOn: {
    backgroundColor: '#10b981',
  },
  statusOff: {
    backgroundColor: '#ef4444',
  },
  statusText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sectionTitle: {
    color: '#f8fafc',
    fontSize: 18,
    fontWeight: '600',
  },
  sectionSubtitle: {
    color: '#94a3b8',
    fontSize: 14,
  },
  list: {
    flex: 1,
  },
  listContent: {
    paddingBottom: 20,
  },
  appItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1e293b',
    borderRadius: 12,
    padding: 14,
    marginBottom: 8,
  },
  appInfo: {
    flex: 1,
    marginRight: 12,
  },
  appName: {
    color: '#f8fafc',
    fontSize: 15,
    fontWeight: '600',
  },
  appPackage: {
    color: '#64748b',
    fontSize: 12,
    marginTop: 2,
  },
  pinInput: {
    backgroundColor: '#1e293b',
    borderRadius: 12,
    padding: 16,
    color: '#f8fafc',
    fontSize: 20,
    textAlign: 'center',
    marginBottom: 12,
    letterSpacing: 8,
  },
  saveBtn: {
    backgroundColor: '#10b981',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  saveBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  cancelBtn: {
    padding: 16,
    alignItems: 'center',
    marginTop: 4,
  },
  cancelBtnText: {
    color: '#94a3b8',
    fontSize: 14,
  },
});
