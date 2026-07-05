import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  SafeAreaView,
  StatusBar,
  NativeModules,
  Alert,
} from 'react-native';

const { FloatingBubble } = NativeModules;

export default function App() {
  const [hasPermission, setHasPermission] = useState(false);
  const [isServiceRunning, setIsServiceRunning] = useState(false);

  useEffect(() => {
    checkPermissionStatus();
  }, []);

  const checkPermissionStatus = async () => {
    try {
      if (FloatingBubble) {
        const granted = await FloatingBubble.hasPermission();
        setHasPermission(granted);
      }
    } catch (e) {
      console.log('Error checking overlay permission:', e);
    }
  };

  const requestOverlayPermission = async () => {
    try {
      if (FloatingBubble) {
        const granted = await FloatingBubble.requestPermission();
        setHasPermission(granted);
        if (granted) {
          Alert.alert('Permission Granted', 'You can now start the floating bubble.');
        } else {
          Alert.alert(
            'Permission Required',
            'Please enable "Draw over other apps" settings to use this feature.'
          );
        }
      }
    } catch (e) {
      console.log('Error requesting overlay permission:', e);
    }
  };

  const startFloatingBubble = async () => {
    if (!hasPermission) {
      Alert.alert(
        'Permission Needed',
        'Please grant "Draw over other apps" permission first.',
        [{ text: 'Grant', onPress: requestOverlayPermission }, { text: 'Cancel' }]
      );
      return;
    }

    try {
      if (FloatingBubble) {
        await FloatingBubble.show();
        setIsServiceRunning(true);
      }
    } catch (e) {
      Alert.alert('Error', 'Failed to start floating bubble service.');
      console.log(e);
    }
  };

  const stopFloatingBubble = async () => {
    try {
      if (FloatingBubble) {
        await FloatingBubble.hide();
        setIsServiceRunning(false);
      }
    } catch (e) {
      console.log(e);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <View style={styles.content}>
        <Text style={styles.title}>Voice Translator Bubble</Text>
        <Text style={styles.subtitle}>Draw Over Other Apps Utility</Text>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Status Panel</Text>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Draw Over Apps Permission:</Text>
            <Text
              style={[
                styles.statusValue,
                { color: hasPermission ? '#00e676' : '#ff1744' },
              ]}
            >
              {hasPermission ? 'GRANTED' : 'REQUIRED'}
            </Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Floating Bubble Status:</Text>
            <Text
              style={[
                styles.statusValue,
                { color: isServiceRunning ? '#00e676' : '#ff8f00' },
              ]}
            >
              {isServiceRunning ? 'RUNNING' : 'STOPPED'}
            </Text>
          </View>
        </View>

        {!hasPermission && (
          <TouchableOpacity
            style={[styles.button, styles.permissionButton]}
            onPress={requestOverlayPermission}
          >
            <Text style={styles.buttonText}>Grant Draw Over Apps Permission</Text>
          </TouchableOpacity>
        )}

        <TouchableOpacity
          style={[styles.button, isServiceRunning ? styles.stopButton : styles.startButton]}
          onPress={isServiceRunning ? stopFloatingBubble : startFloatingBubble}
        >
          <Text style={styles.buttonText}>
            {isServiceRunning ? 'Stop Floating Bubble' : 'Start Floating Bubble'}
          </Text>
        </TouchableOpacity>

        <View style={styles.instructionsCard}>
          <Text style={styles.instructionsTitle}>How to use:</Text>
          <Text style={styles.instructionStep}>
            1. Tap "Start Floating Bubble" to show the violet icon on your screen.
          </Text>
          <Text style={styles.instructionStep}>
            2. Go to any app (like WhatsApp, Instagram, or YouTube).
          </Text>
          <Text style={styles.instructionStep}>
            3. Tap the floating bubble to expand the translation window.
          </Text>
          <Text style={styles.instructionStep}>
            4. Choose languages, tap "Voice Input", speak in Hindi. It translates automatically!
          </Text>
          <Text style={styles.instructionStep}>
            5. Tap "Copy" to copy translation to clipboard and auto-minimize.
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#121212',
  },
  content: {
    flex: 1,
    padding: 24,
    justifyContent: 'center',
    alignItems: 'stretch',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#ffffff',
    textAlign: 'center',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 16,
    color: '#b0bec5',
    textAlign: 'center',
    marginBottom: 32,
  },
  card: {
    backgroundColor: '#1e1e1e',
    borderRadius: 12,
    padding: 16,
    marginBottom: 24,
    borderWidth: 1,
    borderColor: '#37474f',
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#8a2be2',
    marginBottom: 12,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  statusLabel: {
    fontSize: 14,
    color: '#cfd8dc',
  },
  statusValue: {
    fontSize: 14,
    fontWeight: 'bold',
  },
  button: {
    paddingVertical: 14,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 16,
    elevation: 2,
  },
  permissionButton: {
    backgroundColor: '#ff8f00',
  },
  startButton: {
    backgroundColor: '#8a2be2',
  },
  stopButton: {
    backgroundColor: '#c62828',
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: 'bold',
  },
  instructionsCard: {
    backgroundColor: '#1a1a1a',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#263238',
  },
  instructionsTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#cfd8dc',
    marginBottom: 12,
  },
  instructionStep: {
    fontSize: 13,
    color: '#90a4ae',
    lineHeight: 18,
    marginBottom: 8,
  },
});
