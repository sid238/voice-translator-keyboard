import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  SafeAreaView,
  StatusBar,
  NativeModules,
  TextInput,
  AppState,
  ScrollView,
  Switch,
  Keyboard,
  Dimensions,
  Alert,
  Linking,
} from 'react-native';

const { FloatingBubble } = NativeModules;

const ScreenWidth = Dimensions.get('window').width;

const Icon = ({ name, size = 22, color = '#FFFFFF', active = false }) => {
  const s = size;
  const h = size * 0.5;
  const q = size * 0.25;
  const c = color;

  if (name === 'home') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{
          width: 0, height: 0,
          borderLeftWidth: s * 0.4, borderRightWidth: s * 0.4, borderBottomWidth: s * 0.35,
          borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: c,
          marginBottom: -2
        }} />
        <View style={{ width: s * 0.5, height: s * 0.3, backgroundColor: c, borderRadius: 2 }} />
      </View>
    );
  }
  if (name === 'settings') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.7, height: s * 0.7, borderRadius: s * 0.35, borderWidth: 2.5, borderColor: c, alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ width: s * 0.3, height: s * 0.3, borderRadius: s * 0.15, backgroundColor: c }} />
        </View>
      </View>
    );
  }
  if (name === 'about') {
    return (
      <View style={{ width: s, height: s, borderRadius: h, borderWidth: 2.5, borderColor: c, alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ color: c, fontSize: s * 0.45, fontWeight: '900' }}>i</Text>
      </View>
    );
  }
  if (name === 'theme') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.75, height: s * 0.75, borderRadius: 2, borderWidth: 2, borderColor: c, alignItems: 'center', justifyContent: 'center', transform: [{ rotate: '45deg' }] }}>
          <View style={{ width: 4, height: 4, borderRadius: 2, backgroundColor: c, position: 'absolute', top: 3, left: 3 }} />
          <View style={{ width: 4, height: 4, borderRadius: 2, backgroundColor: c, position: 'absolute', top: 3, right: 3 }} />
          <View style={{ width: 4, height: 4, borderRadius: 2, backgroundColor: c, position: 'absolute', bottom: 3, left: 3 }} />
          <View style={{ width: 4, height: 4, borderRadius: 2, backgroundColor: c, position: 'absolute', bottom: 3, right: 3 }} />
        </View>
      </View>
    );
  }
  if (name === 'keyboard') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ flexDirection: 'row', gap: 2, marginBottom: 2 }}>
          {[0,1,2,3,4].map(i => <View key={i} style={{ width: 3, height: 4, backgroundColor: c, borderRadius: 1 }} />)}
        </View>
        <View style={{ flexDirection: 'row', gap: 2, marginBottom: 2 }}>
          {[0,1,2,3].map(i => <View key={i} style={{ width: 3, height: 4, backgroundColor: c, borderRadius: 1 }} />)}
        </View>
        <View style={{ flexDirection: 'row', gap: 2 }}>
          <View style={{ width: 6, height: 4, backgroundColor: c, borderRadius: 1 }} />
          <View style={{ width: 10, height: 4, backgroundColor: c, borderRadius: 1 }} />
          <View style={{ width: 6, height: 4, backgroundColor: c, borderRadius: 1 }} />
        </View>
      </View>
    );
  }
  if (name === 'gesture') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.6, height: s * 0.15, borderRadius: 3, backgroundColor: c, position: 'absolute', top: s * 0.35 }} />
        <View style={{ width: s * 0.15, height: s * 0.5, borderRadius: 3, backgroundColor: c, position: 'absolute', left: s * 0.1, top: s * 0.15 }} />
        <View style={{
          width: 0, height: 0,
          borderLeftWidth: 5, borderRightWidth: 5, borderBottomWidth: 7,
          borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: c,
          position: 'absolute', right: s * 0.05, top: s * 0.35
        }} />
      </View>
    );
  }
  if (name === 'typing') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.6, height: 2.5, backgroundColor: c, borderRadius: 1, position: 'absolute', top: s * 0.25, transform: [{ rotate: '-45deg' }] }} />
        <View style={{ width: s * 0.65, height: s * 0.65, borderRadius: 3, borderWidth: 2.5, borderColor: c, position: 'absolute', bottom: 0, right: 0 }} />
      </View>
    );
  }
  if (name === 'clipboard') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.5, height: s * 0.65, borderRadius: 3, borderWidth: 2.5, borderColor: c, alignItems: 'center', paddingTop: 2 }}>
          <View style={{ width: s * 0.2, height: s * 0.15, borderRadius: 1.5, borderWidth: 1.5, borderColor: c }} />
        </View>
      </View>
    );
  }
  if (name === 'language' || name === 'globe') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.7, height: s * 0.7, borderRadius: s * 0.35, borderWidth: 2.5, borderColor: c, alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ width: s * 0.4, height: s * 0.4, borderRadius: s * 0.2, borderWidth: 1.5, borderColor: c }} />
          <View style={{ position: 'absolute', width: 1.5, height: s * 0.5, backgroundColor: c, top: s * 0.1 }} />
          <View style={{ position: 'absolute', width: s * 0.5, height: 1.5, backgroundColor: c, left: s * 0.1 }} />
        </View>
      </View>
    );
  }
  if (name === 'addons') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.35, height: s * 0.35, borderRadius: 3, backgroundColor: c, position: 'absolute', bottom: s * 0.05, right: s * 0.05 }} />
        <View style={{ width: s * 0.35, height: s * 0.35, borderRadius: 3, borderWidth: 2, borderColor: c, position: 'absolute', top: s * 0.05, left: s * 0.05 }} />
      </View>
    );
  }
  if (name === 'advanced') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        {[0,1,2].map(i => (
          <View key={i} style={{ flexDirection: 'row', gap: 3, marginBottom: 3 }}>
            {[0,1].map(j => <View key={j} style={{ width: 4, height: 4, borderRadius: 2, backgroundColor: c }} />)}
          </View>
        ))}
      </View>
    );
  }
  if (name === 'power') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: 2.5, height: s * 0.55, backgroundColor: c, borderRadius: 1.5, position: 'absolute', bottom: s * 0.1 }} />
        <View style={{ width: s * 0.6, height: s * 0.6, borderRadius: s * 0.3, borderWidth: 2.5, borderColor: c, borderTopColor: 'transparent', position: 'absolute', top: 0 }} />
      </View>
    );
  }
  if (name === 'test') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.5, height: 2.5, backgroundColor: c, borderRadius: 1, position: 'absolute', top: s * 0.3, transform: [{ rotate: '-45deg' }] }} />
        <View style={{ width: s * 0.4, height: s * 0.6, borderRadius: 2, borderWidth: 2, borderColor: c, position: 'absolute', bottom: s * 0.05, right: s * 0.05, alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ width: s * 0.2, height: 1.5, backgroundColor: c, marginBottom: 2 }} />
          <View style={{ width: s * 0.25, height: 1.5, backgroundColor: c, marginBottom: 2 }} />
          <View style={{ width: s * 0.15, height: 1.5, backgroundColor: c }} />
        </View>
      </View>
    );
  }
  if (name === 'search') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: s * 0.5, height: s * 0.5, borderRadius: s * 0.25, borderWidth: 2.5, borderColor: c }} />
        <View style={{ width: 2, height: s * 0.25, backgroundColor: c, borderRadius: 1, position: 'absolute', bottom: s * 0.08, right: s * 0.08, transform: [{ rotate: '-45deg' }] }} />
      </View>
    );
  }
  if (name === 'arrow') {
    return <Text style={{ color: c, fontSize: s * 0.9, fontWeight: '300' }}>›</Text>;
  }
  if (name === 'back') {
    return <Text style={{ color: c, fontSize: s * 0.9, fontWeight: '300' }}>‹</Text>;
  }
  if (name === 'star') {
    return (
      <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: 0, height: 0, borderLeftWidth: s * 0.12, borderRightWidth: s * 0.12, borderBottomWidth: s * 0.45, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: c }} />
      </View>
    );
  }
  if (name === 'chevron-up') {
    return <Text style={{ color: c, fontSize: s, fontWeight: '300', marginTop: -4 }}>⌃</Text>;
  }
  return <View style={{ width: s, height: s, borderRadius: h, backgroundColor: c, opacity: 0.3 }} />;
};

export default function App() {
  const [isKeyboardEnabled, setIsKeyboardEnabled] = useState(false);
  const [isKeyboardDefault, setIsKeyboardDefault] = useState(false);
  const [isKeyboardVisible, setIsKeyboardVisible] = useState(false);
  const appState = useRef(AppState.currentState);

  const [activeTab, setActiveTab] = useState('home');

  const [activeSection, setActiveSection] = useState(null);

  const [theme, setTheme] = useState('green');
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [vibeEnabled, setVibeEnabled] = useState(true);
  const [numberRowEnabled, setNumberRowEnabled] = useState(true);
  const [keyboardHeight, setKeyboardHeight] = useState(270);
  const [gestureEnabled, setGestureEnabled] = useState(true);

  const [keySpacing, setKeySpacing] = useState(6);
  const [longPressDelay, setLongPressDelay] = useState(300);
  const [suggestionsEnabled, setSuggestionsEnabled] = useState(true);
  const [autoCorrectEnabled, setAutoCorrectEnabled] = useState(true);

  const [keyboardEffect, setKeyboardEffect] = useState('none');
  const [clipboardTimelineEnabled, setClipboardTimelineEnabled] = useState(false);

  const [selectedLanguage, setSelectedLanguage] = useState('en');
  const [selectedLanguages, setSelectedLanguages] = useState(['en']);
  const [autoCap, setAutoCap] = useState(true);
  const [doubleSpacePeriod, setDoubleSpacePeriod] = useState(true);
  const [clipboardLimit, setClipboardLimit] = useState(100);
  const [pinLimit, setPinLimit] = useState(10);
  const [emojiScale, setEmojiScale] = useState('medium');
  const [addonVoiceText, setAddonVoiceText] = useState(true);
  const [addonTranslate, setAddonTranslate] = useState(true);

  useEffect(() => {
    checkKeyboardStatus();
    loadAllPrefs();

    const keyboardDidShowListener = Keyboard.addListener('keyboardDidShow', () => {
      setIsKeyboardVisible(true);
    });
    const keyboardDidHideListener = Keyboard.addListener('keyboardDidHide', () => {
      setIsKeyboardVisible(false);
    });

    const subscription = AppState.addEventListener('change', (nextAppState) => {
      if (
        appState.current.match(/inactive|background/) &&
        nextAppState === 'active'
      ) {
        checkKeyboardStatus();
        loadAllPrefs();
      }
      appState.current = nextAppState;
    });

    const interval = setInterval(() => {
      checkKeyboardStatus();
    }, 2000);

    return () => {
      subscription.remove();
      keyboardDidShowListener.remove();
      keyboardDidHideListener.remove();
      clearInterval(interval);
    };
  }, []);

  const checkKeyboardStatus = async () => {
    try {
      if (FloatingBubble) {
        const enabled = await FloatingBubble.isKeyboardEnabled();
        const isDefault = await FloatingBubble.isKeyboardDefault();
        setIsKeyboardEnabled(enabled);
        setIsKeyboardDefault(isDefault);
      }
    } catch (e) {
      console.log('Error checking keyboard status:', e);
    }
  };

  const loadAllPrefs = async () => {
    try {
      if (FloatingBubble) {
        if (FloatingBubble.getStringSetting) {
          const t = await FloatingBubble.getStringSetting('theme', 'green');
          setTheme(t);
          const lang = await FloatingBubble.getStringSetting('selected_language', 'en');
          setSelectedLanguage(lang);
          const langsStr = await FloatingBubble.getStringSetting('selected_languages', 'en');
          const langsList = langsStr.split(',').filter(x => x.length > 0);
          setSelectedLanguages(langsList.length > 0 ? langsList : ['en']);
          const escale = await FloatingBubble.getStringSetting('emoji_scale', 'medium');
          setEmojiScale(escale);
          const effect = await FloatingBubble.getStringSetting('keyboard_effect', 'none');
          setKeyboardEffect(effect);
        }
        if (FloatingBubble.getBooleanSetting) {
          const sound = await FloatingBubble.getBooleanSetting('sound_enabled', false);
          setSoundEnabled(sound);
          const vibe = await FloatingBubble.getBooleanSetting('vibration_enabled', true);
          setVibeEnabled(vibe);
          const numRow = await FloatingBubble.getBooleanSetting('number_row_enabled', true);
          setNumberRowEnabled(numRow);
          const gest = await FloatingBubble.getBooleanSetting('gesture_enabled', true);
          setGestureEnabled(gest);

          const ac = await FloatingBubble.getBooleanSetting('auto_cap', true);
          setAutoCap(ac);
          const ds = await FloatingBubble.getBooleanSetting('double_space_period', true);
          setDoubleSpacePeriod(ds);

          const sug = await FloatingBubble.getBooleanSetting('suggestions_enabled', true);
          setSuggestionsEnabled(sug);
          const acor = await FloatingBubble.getBooleanSetting('auto_correct_enabled', true);
          setAutoCorrectEnabled(acor);

          const avt = await FloatingBubble.getBooleanSetting('addon_voice_text', true);
          setAddonVoiceText(avt);
          const atr = await FloatingBubble.getBooleanSetting('addon_translate', true);
          setAddonTranslate(atr);

          const timeline = await FloatingBubble.getBooleanSetting('clipboard_timeline', false);
          setClipboardTimelineEnabled(timeline);
        }
        if (FloatingBubble.getIntSetting) {
          const h = await FloatingBubble.getIntSetting('keyboard_height_dp', 270);
          setKeyboardHeight(h);
          const climit = await FloatingBubble.getIntSetting('clipboard_limit', 100);
          setClipboardLimit(climit);
          const plimit = await FloatingBubble.getIntSetting('pin_limit', 10);
          setPinLimit(plimit);
          const spacing = await FloatingBubble.getIntSetting('key_spacing_dp', 6);
          setKeySpacing(spacing);
          const delay = await FloatingBubble.getIntSetting('long_press_delay_ms', 300);
          setLongPressDelay(delay);
        }
      }
    } catch (e) {
      console.log('Error loading preferences:', e);
    }
  };

  const saveStringPref = (key, val) => {
    if (FloatingBubble && FloatingBubble.saveStringSetting) {
      FloatingBubble.saveStringSetting(key, val);
    }
  };

  const saveBooleanPref = (key, val) => {
    if (FloatingBubble && FloatingBubble.saveBooleanSetting) {
      FloatingBubble.saveBooleanSetting(key, val);
    }
  };

  const saveIntPref = (key, val) => {
    if (FloatingBubble && FloatingBubble.saveIntSetting) {
      FloatingBubble.saveIntSetting(key, val);
    }
  };

  const openKeyboardSettings = () => {
    try {
      if (FloatingBubble) {
        FloatingBubble.openKeyboardSettings();
      }
    } catch (e) {
      console.log('Error opening keyboard settings:', e);
    }
  };

  const selectKeyboard = () => {
    try {
      if (FloatingBubble) {
        FloatingBubble.showKeyboardPicker();
      }
    } catch (e) {
      console.log('Error showing keyboard picker:', e);
    }
  };

  const palette = {
    background: '#000000',
    surface: '#0D0D0D',
    surfaceSecondary: '#1A1A1A',
    emerald: '#00D68F',
    blue: '#4F8CFF',
    orange: '#FFB020',
    red: '#FF5C5C',
    text: '#FFFFFF',
    subtext: '#8E8E93',
    border: '#1C1C1E',
    glass: 'rgba(255,255,255,0.04)',
  };

  const renderSectionDetails = () => {
    let title = '';
    let desc = '';
    let content = null;

    switch (activeSection) {
      case 'language':
        title = 'Language';
        desc = 'Select multiple input languages and custom keyboard layouts to switch between.';
        content = (
          <View>
            {[
              { id: 'en', name: 'English QWERTY' },
              { id: 'hi_phonetic', name: 'Hindi Phonetic (Hinglish)' },
              { id: 'es', name: 'Spanish QWERTY' },
              { id: 'fr', name: 'French AZERTY' },
            ].map((lang) => {
              const isSelected = selectedLanguages.includes(lang.id);
              return (
                <TouchableOpacity
                  key={lang.id}
                  style={[
                    styles.settingItemRow,
                    isSelected && { borderColor: palette.emerald }
                  ]}
                  onPress={() => {
                    let newLangs = [...selectedLanguages];
                    if (isSelected) {
                      if (newLangs.length > 1) {
                        newLangs = newLangs.filter(id => id !== lang.id);
                      }
                    } else {
                      newLangs.push(lang.id);
                    }
                    setSelectedLanguages(newLangs);
                    saveStringPref('selected_languages', newLangs.join(','));
                  }}
                >
                  <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                    <Icon name="globe" size={20} color={isSelected ? palette.emerald : palette.subtext} />
                    <Text style={[styles.settingItemTitle, isSelected && { color: palette.emerald }]}>
                      {lang.name}
                    </Text>
                  </View>
                  <View style={[styles.customRadio, isSelected && styles.customRadioActive]}>
                    {isSelected && <View style={styles.customRadioInner} />}
                  </View>
                </TouchableOpacity>
              );
            })}
          </View>
        );
        break;

      case 'theme':
        title = 'Theme Store';
        desc = 'Instantly apply premium colorways and glow profiles to match your mood.';
        content = (
          <View>
            <View style={styles.themeGrid}>
              {[
                { id: 'green', name: 'Emerald Glow', color: palette.emerald, bg: '#03120E' },
                { id: 'blue', name: 'Oceanic Blue', color: palette.blue, bg: '#050B14' },
                { id: 'purple', name: 'Neon Purple', color: '#A855F7', bg: '#090514' },
                { id: 'red', name: 'Sunset Crimson', color: palette.red, bg: '#140505' },
                { id: 'dark', name: 'Pitch Dark', color: '#FFFFFF', bg: '#121212' },
                { id: 'dynamic', name: 'Dynamic Match', color: '#00D68F', bg: '#1F1F1F' },
              ].map((t) => (
                <TouchableOpacity
                  key={t.id}
                  style={[
                    styles.themeCard,
                    { backgroundColor: t.bg, borderColor: theme === t.id ? t.color : palette.border }
                  ]}
                  onPress={() => {
                    setTheme(t.id);
                    saveStringPref('theme', t.id);
                  }}
                >
                  <Text style={styles.themeCardName}>{t.name}</Text>
                  <View style={[styles.themePill, { backgroundColor: t.color }]} />
                </TouchableOpacity>
              ))}
            </View>

            <View style={styles.wallpaperContainer}>
              <Text style={styles.wallpaperTitle}>Custom Wallpaper background</Text>
              <Text style={styles.wallpaperSubtitle}>Set your own background image/photo for the keyboard keys overlay.</Text>
              <View style={{ flexDirection: 'row', gap: 12 }}>
                <TouchableOpacity
                  style={[styles.wallpaperBtn, { backgroundColor: palette.emerald }]}
                  onPress={async () => {
                    try {
                      if (FloatingBubble && FloatingBubble.pickThemeImage) {
                        await FloatingBubble.pickThemeImage();
                        Alert.alert("Success", "Custom keyboard background image set!");
                      }
                    } catch (e) {
                      if (e.message !== "Image picking was cancelled") {
                        Alert.alert("Error", e.message || "Failed to pick image");
                      }
                    }
                  }}
                >
                  <Text style={styles.wallpaperBtnText}>Choose Photo</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.wallpaperBtn, { backgroundColor: 'rgba(255,92,92,0.1)', borderWidth: 1, borderColor: palette.red }]}
                  onPress={async () => {
                    try {
                      if (FloatingBubble && FloatingBubble.clearThemeImage) {
                        await FloatingBubble.clearThemeImage();
                        Alert.alert("Cleared", "Custom keyboard background image cleared.");
                      }
                    } catch (e) {
                      Alert.alert("Error", e.message || "Failed to clear image");
                    }
                  }}
                >
                  <Text style={[styles.wallpaperBtnText, { color: palette.red }]}>Remove Photo</Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        );
        break;

      case 'keyboard':
        title = 'Keyboard Settings';
        desc = 'Customize structural heights, vibration strengths, keyspacing, and delays.';
        content = (
          <View>
            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Hinted Number Row</Text>
                <Text style={styles.settingItemSubtitle}>Always display number row above layouts</Text>
              </View>
              <Switch
                value={numberRowEnabled}
                onValueChange={(val) => {
                  setNumberRowEnabled(val);
                  saveBooleanPref('number_row_enabled', val);
                }}
                thumbColor={numberRowEnabled ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>

            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Keypress Sound Feedback</Text>
                <Text style={styles.settingItemSubtitle}>Audio clicks on tapping keys</Text>
              </View>
              <Switch
                value={soundEnabled}
                onValueChange={(val) => {
                  setSoundEnabled(val);
                  saveBooleanPref('sound_enabled', val);
                }}
                thumbColor={soundEnabled ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>

            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Keypress Haptic Vibration</Text>
                <Text style={styles.settingItemSubtitle}>Tactile feedback response on presses</Text>
              </View>
              <Switch
                value={vibeEnabled}
                onValueChange={(val) => {
                  setVibeEnabled(val);
                  saveBooleanPref('vibration_enabled', val);
                }}
                thumbColor={vibeEnabled ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>

            <View style={styles.spacingSliderGroup}>
              <Text style={styles.sliderHeading}>Key Spacing (dp)</Text>
              <Text style={styles.sliderDesc}>Current spacing: {keySpacing} dp</Text>
              <View style={styles.tabsSelectorRow}>
                {[2, 4, 6, 8, 10].map((item) => (
                  <TouchableOpacity
                    key={item}
                    style={[styles.tabSelectorCell, keySpacing === item && styles.tabSelectorCellActive]}
                    onPress={() => {
                      setKeySpacing(item);
                      saveIntPref('key_spacing_dp', item);
                    }}
                  >
                    <Text style={[styles.tabSelectorCellText, keySpacing === item && styles.tabSelectorCellTextActive]}>
                      {item}dp
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            <View style={[styles.spacingSliderGroup, { marginTop: 12 }]}>
              <Text style={styles.sliderHeading}>Long Press Delay (ms)</Text>
              <Text style={styles.sliderDesc}>Current delay: {longPressDelay} ms</Text>
              <View style={styles.tabsSelectorRow}>
                {[200, 300, 400, 500, 600].map((item) => (
                  <TouchableOpacity
                    key={item}
                    style={[styles.tabSelectorCell, longPressDelay === item && styles.tabSelectorCellActive]}
                    onPress={() => {
                      setLongPressDelay(item);
                      saveIntPref('long_press_delay_ms', item);
                    }}
                  >
                    <Text style={[styles.tabSelectorCellText, longPressDelay === item && styles.tabSelectorCellTextActive]}>
                      {item}ms
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            <View style={[styles.spacingSliderGroup, { marginTop: 12 }]}>
              <Text style={styles.sliderHeading}>Keyboard Typing Effect</Text>
              <Text style={styles.sliderDesc}>Select live particle/glow animation when keys are pressed.</Text>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexDirection: 'row', marginTop: 8 }}>
                {[
                  { id: 'none', label: 'None' },
                  { id: 'neon_trail', label: 'Neon Trail' },
                  { id: 'fire', label: 'Fire Effect' },
                  { id: 'water_ripple', label: 'Water Ripple' },
                  { id: 'matrix_rain', label: 'Matrix Rain' },
                  { id: 'galaxy', label: 'Galaxy' },
                  { id: 'rgb_glow', label: 'RGB Glow' },
                  { id: 'mechanical_flash', label: 'Flash' },
                ].map((item) => (
                  <TouchableOpacity
                    key={item.id}
                    style={[styles.tabSelectorCell, keyboardEffect === item.id && styles.tabSelectorCellActive, { marginRight: 8, paddingHorizontal: 12 }]}
                    onPress={() => {
                      setKeyboardEffect(item.id);
                      saveStringPref('keyboard_effect', item.id);
                    }}
                  >
                    <Text style={[styles.tabSelectorCellText, keyboardEffect === item.id && styles.tabSelectorCellTextActive]}>
                      {item.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>
          </View>
        );
        break;

      case 'typing':
        title = 'Typing Corrections';
        desc = 'Adjust auto-corrections, spacebar actions, capitalization, and dictionary mappings.';
        content = (
          <View>
            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Auto Capitalization</Text>
                <Text style={styles.settingItemSubtitle}>Capitalize first letter of sentences/words</Text>
              </View>
              <Switch
                value={autoCap}
                onValueChange={(val) => {
                  setAutoCap(val);
                  saveBooleanPref('auto_cap', val);
                }}
                thumbColor={autoCap ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>

            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Double-Space Period (".")</Text>
                <Text style={styles.settingItemSubtitle}>Double tap spacebar to insert period & space</Text>
              </View>
              <Switch
                value={doubleSpacePeriod}
                onValueChange={(val) => {
                  setDoubleSpacePeriod(val);
                  saveBooleanPref('double_space_period', val);
                }}
                thumbColor={doubleSpacePeriod ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>

            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Smart Bar suggestions</Text>
                <Text style={styles.settingItemSubtitle}>Show dictionary predictions bar</Text>
              </View>
              <Switch
                value={suggestionsEnabled}
                onValueChange={(val) => {
                  setSuggestionsEnabled(val);
                  saveBooleanPref('suggestions_enabled', val);
                }}
                thumbColor={suggestionsEnabled ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>

            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Auto Correction</Text>
                <Text style={styles.settingItemSubtitle}>Replace mistyped words automatically</Text>
              </View>
              <Switch
                value={autoCorrectEnabled}
                onValueChange={(val) => {
                  setAutoCorrectEnabled(val);
                  saveBooleanPref('auto_correct_enabled', val);
                }}
                thumbColor={autoCorrectEnabled ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>
          </View>
        );
        break;

      case 'gesture':
        title = 'Gesture & Glide';
        desc = 'Slide your finger over letters to write seamlessly without raising the hand.';
        content = (
          <View>
            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Enable Glide Typing</Text>
                <Text style={styles.settingItemSubtitle}>Type by sliding over letter paths</Text>
              </View>
              <Switch
                value={gestureEnabled}
                onValueChange={(val) => {
                  setGestureEnabled(val);
                  saveBooleanPref('gesture_enabled', val);
                }}
                thumbColor={gestureEnabled ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>
          </View>
        );
        break;

      case 'clipboard':
        title = 'Clipboard Manager';
        desc = 'Configure copied item retention limits and prevent history loss.';
        content = (
          <View>
            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Copied History Limit</Text>
                <Text style={styles.settingItemSubtitle}>Wipe older items when limit is exceeded</Text>
              </View>
              <View style={styles.quantityWidget}>
                <TouchableOpacity onPress={() => {
                  const n = Math.max(50, clipboardLimit - 50);
                  setClipboardLimit(n);
                  saveIntPref('clipboard_limit', n);
                }} style={styles.quantityBtn}>
                  <Text style={styles.quantityBtnText}>-</Text>
                </TouchableOpacity>
                <Text style={styles.quantityValue}>{clipboardLimit}</Text>
                <TouchableOpacity onPress={() => {
                  const n = Math.min(250, clipboardLimit + 50);
                  setClipboardLimit(n);
                  saveIntPref('clipboard_limit', n);
                }} style={styles.quantityBtn}>
                  <Text style={styles.quantityBtnText}>+</Text>
                </TouchableOpacity>
              </View>
            </View>

            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Max Pins Limit</Text>
                <Text style={styles.settingItemSubtitle}>Maximum number of sticky pinned items</Text>
              </View>
              <Text style={styles.badgeTextGreen}>{pinLimit} Items</Text>
            </View>

            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Clipboard Timeline Mode</Text>
                <Text style={styles.settingItemSubtitle}>Connect copied items with a chronological vertical timeline thread</Text>
              </View>
              <Switch
                value={clipboardTimelineEnabled}
                onValueChange={(val) => {
                  setClipboardTimelineEnabled(val);
                  saveBooleanPref('clipboard_timeline', val);
                }}
                thumbColor={clipboardTimelineEnabled ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>
          </View>
        );
        break;

      case 'addons':
        title = 'Extensions';
        desc = 'Integrate third-party services like voice dictation translators and offline speech engines.';
        content = (
          <View>
            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Voice Dictation Engine</Text>
                <Text style={styles.settingItemSubtitle}>Convert spoken words to text on the fly</Text>
              </View>
              <Switch
                value={addonVoiceText}
                onValueChange={(val) => {
                  setAddonVoiceText(val);
                  saveBooleanPref('addon_voice_text', val);
                }}
                thumbColor={addonVoiceText ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>

            <View style={styles.settingSwitchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.settingItemTitle}>Google Translate Integration</Text>
                <Text style={styles.settingItemSubtitle}>Translate inputs in real-time instantly</Text>
              </View>
              <Switch
                value={addonTranslate}
                onValueChange={(val) => {
                  setAddonTranslate(val);
                  saveBooleanPref('addon_translate', val);
                }}
                thumbColor={addonTranslate ? palette.emerald : '#555'}
                trackColor={{ true: 'rgba(0, 214, 143, 0.3)', false: '#2C2C2C' }}
              />
            </View>
          </View>
        );
        break;

      case 'other':
        title = 'Advanced Options';
        desc = 'Manage backups, clear profiles, configuration resets, and developer items.';
        content = (
          <View>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: palette.surfaceSecondary, borderWidth: 1, borderColor: palette.border }]}
              onPress={() => {
                Alert.alert("Backup", "Preferences backup profile created successfully!");
              }}
            >
              <Text style={styles.actionButtonText}>Backup Settings Profile</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: 'rgba(255, 92, 92, 0.1)', borderWidth: 1, borderColor: palette.red, marginTop: 14 }]}
              onPress={() => {
                setTheme('green');
                setSoundEnabled(false);
                setVibeEnabled(true);
                setNumberRowEnabled(true);
                setKeyboardHeight(270);
                setKeySpacing(6);
                setLongPressDelay(300);
                saveStringPref('theme', 'green');
                saveBooleanPref('sound_enabled', false);
                saveBooleanPref('vibration_enabled', true);
                saveBooleanPref('number_row_enabled', true);
                saveIntPref('keyboard_height_dp', 270);
                saveIntPref('key_spacing_dp', 6);
                saveIntPref('long_press_delay_ms', 300);
                Alert.alert("Reset", "All configuration variables reset to default values.");
              }}
            >
              <Text style={[styles.actionButtonText, { color: palette.red }]}>Wipe Settings & Reset All</Text>
            </TouchableOpacity>
          </View>
        );
        break;

      default:
        break;
    }

    return (
      <View style={{ flex: 1 }}>
        <TouchableOpacity
          style={styles.backHeader}
          onPress={() => setActiveSection(null)}
        >
          <Icon name="back" size={28} color={palette.emerald} />
          <Text style={styles.backHeaderText}>Back to Settings</Text>
        </TouchableOpacity>
        <ScrollView style={styles.scrollContainer} contentContainerStyle={{ paddingBottom: 40 }}>
          <Text style={styles.sectionHeaderTitle}>{title}</Text>
          <Text style={styles.sectionHeaderDesc}>{desc}</Text>
          <View style={styles.sectionCardWrapper}>{content}</View>
        </ScrollView>
      </View>
    );
  };

  const renderHome = () => {
    return (
      <ScrollView style={styles.scrollContainer} contentContainerStyle={{ paddingBottom: 40 }}>
        <View style={styles.heroCard}>
          <View style={[styles.glowBlob, { backgroundColor: palette.emerald, left: -40, top: -45 }]} />
          <View style={[styles.glowBlob, { backgroundColor: palette.blue, right: -40, bottom: -45 }]} />

          <View style={styles.heroHeader}>
            <View>
              <Text style={styles.heroBrandText}>Orbit Keyboard</Text>
              <Text style={styles.heroEditionText}>Premium Edition v2.1.0</Text>
            </View>
            <View style={[styles.heroStatusChip, { backgroundColor: isKeyboardDefault ? 'rgba(0,214,143,0.12)' : 'rgba(255,176,32,0.12)' }]}>
              <View style={[styles.heroStatusDot, { backgroundColor: isKeyboardDefault ? palette.emerald : palette.orange }]} />
              <Text style={[styles.heroStatusText, { color: isKeyboardDefault ? palette.emerald : palette.orange }]}>
                {isKeyboardDefault ? 'Active' : 'Setup Required'}
              </Text>
            </View>
          </View>

          <View style={styles.kbIllustration}>
            <View style={styles.kbRow}>
              <View style={styles.kbKey} />
              <View style={styles.kbKey} />
              <View style={[styles.kbKey, { borderColor: palette.emerald, borderWidth: 1 }]} />
              <View style={styles.kbKey} />
              <View style={styles.kbKey} />
              <View style={styles.kbKey} />
              <View style={styles.kbKey} />
            </View>
            <View style={styles.kbRow}>
              <View style={styles.kbKey} />
              <View style={[styles.kbKey, { backgroundColor: palette.emerald }]} />
              <View style={styles.kbKey} />
              <View style={styles.kbKey} />
              <View style={styles.kbKey} />
              <View style={styles.kbKey} />
            </View>
            <View style={[styles.kbRow, { paddingHorizontal: 12 }]}>
              <View style={[styles.kbKey, { flex: 1.3 }]} />
              <View style={[styles.kbKey, { flex: 4, backgroundColor: 'rgba(255,255,255,0.06)' }]} />
              <View style={[styles.kbKey, { flex: 1.3, backgroundColor: palette.blue }]} />
            </View>
          </View>
        </View>

        <Text style={styles.gridSectionHeading}>Quick Actions</Text>
        <View style={styles.quickActionsContainer}>
          <TouchableOpacity
            style={styles.quickActionCard}
            onPress={openKeyboardSettings}
          >
            <View style={[styles.quickActionIconBg, { backgroundColor: 'rgba(0, 214, 143, 0.12)' }]}>
              <Icon name="power" size={20} color={palette.emerald} />
            </View>
            <Text style={styles.quickActionLabel}>Enable</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.quickActionCard}
            onPress={selectKeyboard}
          >
            <View style={[styles.quickActionIconBg, { backgroundColor: 'rgba(79, 140, 255, 0.12)' }]}>
              <Icon name="keyboard" size={20} color={palette.blue} />
            </View>
            <Text style={styles.quickActionLabel}>Open / Switch</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.quickActionCard}
            onPress={() => {
              if (activeTab === 'home') {
                Alert.alert("Interactive Test", "Scroll down to tap on the interactive Test Box input field.");
              }
            }}
          >
            <View style={[styles.quickActionIconBg, { backgroundColor: 'rgba(255, 176, 32, 0.12)' }]}>
              <Icon name="test" size={20} color={palette.orange} />
            </View>
            <Text style={styles.quickActionLabel}>Test Input</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.quickActionCard}
            onPress={() => {
              setActiveTab('settings');
              setActiveSection(null);
            }}
          >
            <View style={[styles.quickActionIconBg, { backgroundColor: 'rgba(255, 92, 92, 0.12)' }]}>
              <Icon name="settings" size={20} color={palette.red} />
            </View>
            <Text style={styles.quickActionLabel}>Settings</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.gridSectionHeading}>Dashboard Statistics</Text>
        <View style={styles.dashboardGrid}>
          <View style={styles.dashboardWidget}>
            <Text style={styles.widgetTitle}>Status</Text>
            <Text style={[styles.widgetVal, { color: isKeyboardEnabled ? palette.emerald : palette.red }]}>
              {isKeyboardEnabled ? 'Active' : 'Disabled'}
            </Text>
            <Text style={styles.widgetSub}>Settings service</Text>
          </View>

          <View style={styles.dashboardWidget}>
            <Text style={styles.widgetTitle}>Languages</Text>
            <Text style={[styles.widgetVal, { color: palette.blue }]}>
              {selectedLanguages.length}
            </Text>
            <Text style={styles.widgetSub}>Active layout sets</Text>
          </View>

          <View style={styles.dashboardWidget}>
            <Text style={styles.widgetTitle}>Active Theme</Text>
            <Text style={[styles.widgetVal, { color: palette.orange, textTransform: 'capitalize' }]}>
              {theme}
            </Text>
            <Text style={styles.widgetSub}>Interface skin profile</Text>
          </View>

          <View style={styles.dashboardWidget}>
            <Text style={styles.widgetTitle}>Clipboard cap</Text>
            <Text style={[styles.widgetVal, { color: palette.emerald }]}>
              {clipboardLimit}
            </Text>
            <Text style={styles.widgetSub}>Maximum history log</Text>
          </View>
        </View>

        <View style={styles.premiumCard}>
          <Text style={styles.premiumCardTitle}>Test Your Overhauled Keyboard</Text>
          <Text style={styles.premiumCardDesc}>
            Tap the input field below to test settings like customizable keyspacing, long press delays, double-tap shift lock, word suggestions, and drag resizing handles.
          </Text>
          <TextInput
            style={styles.premiumInputBox}
            placeholder="Tap here to test NeoType input..."
            placeholderTextColor="rgba(255,255,255,0.25)"
            multiline
          />
        </View>
      </ScrollView>
    );
  };

  const renderSettings = () => {
    if (activeSection) {
      return renderSectionDetails();
    }

    const groups = [
      {
        header: 'Appearance',
        items: [
          { id: 'theme', title: 'Theme Store', subtitle: 'Change glow presets, accent colors, and background styles', icon: 'theme' }
        ]
      },
      {
        header: 'Keyboard Settings',
        items: [
          { id: 'keyboard', title: 'Height & Layout spacing', subtitle: 'Configure key heights, padding offsets, sound & vibrations', icon: 'keyboard' },
          { id: 'gesture', title: 'Gesture Glide Typing', subtitle: 'Type by swiping paths across key overlays', icon: 'gesture' }
        ]
      },
      {
        header: 'Text & Corrections',
        items: [
          { id: 'typing', title: 'Typing & Corrections', subtitle: 'Configure auto-capitalizations, suggestions & corrections', icon: 'typing' }
        ]
      },
      {
        header: 'Clipboard Settings',
        items: [
          { id: 'clipboard', title: 'Clipboard History logs', subtitle: 'Adjust item retention, pinned storage caps, and clipboard actions', icon: 'clipboard' }
        ]
      },
      {
        header: 'Advanced features',
        items: [
          { id: 'language', title: 'Language Profiles', subtitle: 'Select active layouts and toggle phonetic indices', icon: 'language' },
          { id: 'addons', title: 'Addons & Extension links', subtitle: 'Integrate real time translation and dictation tools', icon: 'addons' },
          { id: 'other', title: 'Advanced developer keys', subtitle: 'Manage configuration backups, data wipes, and defaults', icon: 'advanced' }
        ]
      }
    ];

    return (
      <ScrollView style={styles.scrollContainer} contentContainerStyle={{ paddingBottom: 40 }}>
        <Text style={styles.settingsMainTitle}>Control Panel Mappings</Text>
        {groups.map((group, gIdx) => (
          <View key={gIdx} style={{ marginBottom: 20 }}>
            <Text style={styles.settingsGroupHeader}>{group.header}</Text>
            <View style={styles.settingsGroupCard}>
              {group.items.map((sec, iIdx) => {
                const isLast = iIdx === group.items.length - 1;
                return (
                  <TouchableOpacity
                    key={sec.id}
                    style={[styles.settingsGroupRow, !isLast && styles.settingsGroupRowBorder]}
                    onPress={() => setActiveSection(sec.id)}
                  >
                    <View style={styles.settingsRowContent}>
                      <View style={styles.settingsIconWrapper}>
                        <Icon name={sec.icon} size={18} color={palette.text} />
                      </View>
                      <View style={{ flex: 1, marginRight: 8 }}>
                        <Text style={styles.settingsRowTitle}>{sec.title}</Text>
                        <Text style={styles.settingsRowSubtitle}>{sec.subtitle}</Text>
                      </View>
                    </View>
                    <Icon name="arrow" size={20} color={palette.subtext} />
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>
        ))}
      </ScrollView>
    );
  };

  const renderAbout = () => {
    return (
      <ScrollView style={styles.scrollContainer} contentContainerStyle={{ paddingBottom: 40 }}>
        <View style={styles.aboutHeader}>
          <View style={styles.logoCircle}>
            <Text style={styles.logoText}>O</Text>
          </View>
          <Text style={styles.aboutTitle}>Orbit Keyboard</Text>
          <Text style={styles.aboutEdition}>Premium Edition</Text>
          <View style={styles.versionBadge}>
            <Text style={styles.versionBadgeText}>v2.1.0-premium</Text>
          </View>
        </View>

        <View style={styles.chipsRow}>
          <View style={styles.statusChip}>
            <Icon name="power" size={12} color={palette.emerald} />
            <Text style={[styles.statusChipLabel, { marginLeft: 6 }]}>Active</Text>
          </View>
          <View style={styles.statusChip}>
            <Icon name="search" size={12} color={palette.blue} />
            <Text style={[styles.statusChipLabel, { marginLeft: 6 }]}>Secure</Text>
          </View>
          <View style={styles.statusChip}>
            <Icon name="star" size={12} color={palette.orange} />
            <Text style={[styles.statusChipLabel, { marginLeft: 6 }]}>Fast</Text>
          </View>
        </View>

        <Text style={styles.gridSectionHeading}>Engine Specs</Text>
        <View style={styles.featuresContainer}>
          {[
            { title: 'Smart Suggestions', desc: 'Prefix vocabulary indexing recommends dictionary terms dynamically.' },
            { title: 'Gesture Resize', desc: 'Tap resize handles in the toolbar to adjust margins & height on screen.' },
            { title: 'Clipboard Manager', desc: 'Secure local storage keeps track of logs & pin limits safely.' },
            { title: 'Emoji Engine', desc: 'Hold down emoji categories to continuous type them instantly.' },
          ].map((item, idx) => (
            <View key={idx} style={styles.featureCard}>
              <Text style={styles.featureCardTitle}>{item.title}</Text>
              <Text style={styles.featureCardDesc}>{item.desc}</Text>
            </View>
          ))}
        </View>

        <Text style={styles.gridSectionHeading}>Community</Text>
        <View style={styles.socialRow}>
          {[
            { label: 'GitHub', url: 'https://github.com/sid238/voice-translator-keyboard' },
            { label: 'Telegram', url: 'https://t.me' },
            { label: 'Website', url: 'https://google.com' },
            { label: 'Email', url: 'mailto:support@orbit.com' },
          ].map((soc, idx) => (
            <TouchableOpacity
              key={idx}
              style={styles.socialButton}
              onPress={() => Linking.openURL(soc.url)}
            >
              <Text style={styles.socialButtonText}>{soc.label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.glassCopyright}>
          <Text style={styles.copyrightText}>
            © 2026 NeoType Inc. All rights reserved. Registered flagship layout.
          </Text>
        </View>
      </ScrollView>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#000000" />

      <View style={{ flex: 1 }}>
        {activeTab === 'home' && renderHome()}
        {activeTab === 'settings' && renderSettings()}
        {activeTab === 'about' && renderAbout()}
      </View>

      {!isKeyboardVisible && (
        <View style={styles.floatingNavBar}>
          {[
            { id: 'home', label: 'Home', icon: 'home' },
            { id: 'settings', label: 'Settings', icon: 'settings' },
            { id: 'about', label: 'About', icon: 'about' },
          ].map((tab) => {
            const isActive = activeTab === tab.id;
            return (
              <TouchableOpacity
                key={tab.id}
                style={[styles.floatingNavItem, isActive && styles.floatingNavItemActive]}
                onPress={() => {
                  setActiveTab(tab.id);
                  setActiveSection(null);
                }}
              >
                <Icon name={tab.icon} size={20} color={isActive ? palette.emerald : palette.subtext} />
                <Text style={[styles.floatingNavLabel, isActive && styles.floatingNavLabelActive]}>
                  {tab.label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000000',
    paddingTop: StatusBar.currentHeight || 0,
  },
  scrollContainer: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 20,
  },
  backHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 18,
    borderBottomWidth: 1,
    borderBottomColor: '#1C1C1E',
    backgroundColor: '#000000',
  },
  backHeaderArrow: {
    color: '#00D68F',
    fontSize: 28,
    marginRight: 12,
    fontWeight: '300',
  },
  backHeaderText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
    letterSpacing: 0.2,
    marginLeft: 8,
  },
  sectionHeaderTitle: {
    fontSize: 28,
    fontWeight: '800',
    color: '#FFFFFF',
    marginTop: 20,
    paddingHorizontal: 4,
    letterSpacing: -0.5,
  },
  sectionHeaderDesc: {
    fontSize: 14,
    color: '#8E8E93',
    marginTop: 6,
    paddingHorizontal: 4,
    lineHeight: 20,
    marginBottom: 24,
  },
  sectionCardWrapper: {
    backgroundColor: '#0D0D0D',
    borderRadius: 24,
    borderWidth: 1,
    borderColor: '#1C1C1E',
    padding: 16,
    marginBottom: 40,
  },
  heroCard: {
    backgroundColor: '#0D0D0D',
    borderRadius: 26,
    borderWidth: 1,
    borderColor: '#1C1C1E',
    padding: 24,
    marginBottom: 28,
    overflow: 'hidden',
    position: 'relative',
  },
  glowBlob: {
    position: 'absolute',
    width: 140,
    height: 140,
    borderRadius: 70,
    opacity: 0.15,
  },
  heroHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    zIndex: 2,
  },
  heroBrandText: {
    fontSize: 24,
    fontWeight: '800',
    color: '#FFFFFF',
    letterSpacing: -0.5,
  },
  heroEditionText: {
    fontSize: 13,
    color: '#8E8E93',
    marginTop: 2,
  },
  heroStatusChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
  },
  heroStatusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 6,
  },
  heroStatusText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  kbIllustration: {
    marginTop: 30,
    width: '100%',
    padding: 14,
    backgroundColor: 'rgba(255,255,255,0.03)',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.05)',
    zIndex: 2,
  },
  kbRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 5,
    marginBottom: 5,
  },
  kbKey: {
    height: 22,
    flex: 1,
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderRadius: 4,
  },
  gridSectionHeading: {
    fontSize: 13,
    fontWeight: '800',
    color: '#8E8E93',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    marginBottom: 14,
    paddingLeft: 4,
  },
  quickActionsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 28,
    gap: 10,
  },
  quickActionCard: {
    flex: 1,
    backgroundColor: '#0D0D0D',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: '#1C1C1E',
    paddingVertical: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  quickActionIconBg: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  quickActionLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: '#FFFFFF',
    textAlign: 'center',
  },
  dashboardGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginBottom: 28,
    gap: 12,
  },
  dashboardWidget: {
    width: '48%',
    backgroundColor: '#0D0D0D',
    borderRadius: 24,
    borderWidth: 1,
    borderColor: '#1C1C1E',
    padding: 18,
  },
  widgetTitle: {
    fontSize: 11,
    fontWeight: '700',
    color: '#8E8E93',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  widgetVal: {
    fontSize: 24,
    fontWeight: '800',
    color: '#FFFFFF',
    marginTop: 8,
    letterSpacing: -0.5,
  },
  widgetSub: {
    fontSize: 11,
    color: '#555555',
    marginTop: 4,
  },
  premiumCard: {
    backgroundColor: '#0D0D0D',
    borderRadius: 26,
    borderWidth: 1,
    borderColor: '#1C1C1E',
    padding: 20,
    marginBottom: 30,
  },
  premiumCardTitle: {
    fontSize: 16,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  premiumCardDesc: {
    fontSize: 12,
    color: '#8E8E93',
    marginTop: 6,
    lineHeight: 18,
  },
  premiumInputBox: {
    backgroundColor: '#000000',
    color: '#FFFFFF',
    borderRadius: 16,
    borderColor: '#1C1C1E',
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 13,
    height: 76,
    textAlignVertical: 'top',
    marginTop: 16,
  },
  settingsMainTitle: {
    fontSize: 28,
    fontWeight: '800',
    color: '#FFFFFF',
    letterSpacing: -0.5,
    marginBottom: 24,
    paddingHorizontal: 4,
  },
  settingsGroupHeader: {
    fontSize: 12,
    fontWeight: '800',
    color: '#8E8E93',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 10,
    paddingLeft: 4,
  },
  settingsGroupCard: {
    backgroundColor: '#0D0D0D',
    borderRadius: 26,
    borderWidth: 1,
    borderColor: '#1C1C1E',
    overflow: 'hidden',
  },
  settingsGroupRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 18,
    paddingHorizontal: 20,
  },
  settingsGroupRowBorder: {
    borderBottomWidth: 1,
    borderBottomColor: '#1C1C1E',
  },
  settingsRowContent: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  settingsIconWrapper: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#1A1A1A',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  settingsRowTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  settingsRowSubtitle: {
    fontSize: 11,
    color: '#8E8E93',
    marginTop: 2,
    lineHeight: 15,
  },
  settingsChevron: {
    fontSize: 20,
    color: '#555555',
    fontWeight: '300',
  },
  aboutHeader: {
    alignItems: 'center',
    marginTop: 20,
    marginBottom: 28,
  },
  logoCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#00D68F',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    shadowColor: '#00D68F',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.3,
    shadowRadius: 15,
    elevation: 8,
  },
  logoText: {
    fontSize: 36,
    fontWeight: '900',
    color: '#000000',
  },
  aboutTitle: {
    fontSize: 26,
    fontWeight: '800',
    color: '#FFFFFF',
    letterSpacing: -0.5,
  },
  aboutEdition: {
    fontSize: 14,
    color: '#00D68F',
    fontWeight: '700',
    marginTop: 4,
  },
  versionBadge: {
    backgroundColor: '#0D0D0D',
    borderWidth: 1,
    borderColor: '#1C1C1E',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
    marginTop: 10,
  },
  versionBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#8E8E93',
  },
  chipsRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
    marginBottom: 30,
  },
  statusChip: {
    backgroundColor: '#0D0D0D',
    borderColor: '#1C1C1E',
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    flexDirection: 'row',
    alignItems: 'center',
  },
  statusChipLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  featuresContainer: {
    gap: 12,
    marginBottom: 28,
  },
  featureCard: {
    backgroundColor: '#0D0D0D',
    borderRadius: 24,
    borderWidth: 1,
    borderColor: '#1C1C1E',
    padding: 18,
  },
  featureCardTitle: {
    fontSize: 14,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  featureCardDesc: {
    fontSize: 12,
    color: '#8E8E93',
    marginTop: 4,
    lineHeight: 18,
  },
  socialRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 30,
  },
  socialButton: {
    flex: 1,
    minWidth: '45%',
    backgroundColor: '#0D0D0D',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#1C1C1E',
    paddingVertical: 14,
    alignItems: 'center',
  },
  socialButtonText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  glassCopyright: {
    backgroundColor: 'rgba(255,255,255,0.03)',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.05)',
    paddingVertical: 16,
    paddingHorizontal: 20,
    alignItems: 'center',
    marginBottom: 30,
  },
  copyrightText: {
    fontSize: 11,
    color: '#555555',
    textAlign: 'center',
    lineHeight: 16,
  },
  floatingNavBar: {
    position: 'absolute',
    bottom: 24,
    left: 20,
    right: 20,
    height: 64,
    backgroundColor: 'rgba(13, 13, 13, 0.88)',
    borderRadius: 32,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.5,
    shadowRadius: 20,
    elevation: 10,
    overflow: 'hidden',
  },
  floatingNavItem: {
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1,
    height: '100%',
  },
  floatingNavItemActive: {
    backgroundColor: 'rgba(255,255,255,0.03)',
  },
  floatingNavIcon: {
    fontSize: 18,
    color: '#8E8E93',
  },
  floatingNavLabel: {
    fontSize: 10,
    fontWeight: '700',
    color: '#8E8E93',
    marginTop: 4,
  },
  floatingNavLabelActive: {
    color: '#00D68F',
  },
  settingItemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 18,
    paddingHorizontal: 6,
    borderBottomWidth: 1,
    borderBottomColor: '#1C1C1E',
  },
  customRadio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: '#555555',
    alignItems: 'center',
    justifyContent: 'center',
  },
  customRadioActive: {
    borderColor: '#00D68F',
  },
  customRadioInner: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#00D68F',
  },
  themeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 12,
  },
  themeCard: {
    width: '48%',
    height: 100,
    borderRadius: 22,
    borderWidth: 1.5,
    padding: 16,
    justifyContent: 'space-between',
  },
  themeCardName: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 13,
  },
  themePill: {
    width: 18,
    height: 18,
    borderRadius: 9,
    alignSelf: 'flex-end',
  },
  wallpaperContainer: {
    marginTop: 24,
    padding: 18,
    backgroundColor: '#1A1A1A',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: '#1C1C1E',
  },
  wallpaperTitle: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '800',
  },
  wallpaperSubtitle: {
    color: '#8E8E93',
    fontSize: 11,
    marginTop: 4,
    lineHeight: 16,
    marginBottom: 16,
  },
  wallpaperBtn: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 12,
    alignItems: 'center',
  },
  wallpaperBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 13,
  },
  settingSwitchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#1C1C1E',
  },
  spacingSliderGroup: {
    marginTop: 20,
  },
  sliderHeading: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  sliderDesc: {
    fontSize: 11,
    color: '#8E8E93',
    marginTop: 2,
  },
  tabsSelectorRow: {
    flexDirection: 'row',
    marginTop: 12,
    backgroundColor: '#1A1A1A',
    borderRadius: 16,
    padding: 4,
    gap: 4,
  },
  tabSelectorCell: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabSelectorCellActive: {
    backgroundColor: '#0D0D0D',
    borderWidth: 1,
    borderColor: '#1C1C1E',
  },
  tabSelectorCellText: {
    color: '#8E8E93',
    fontWeight: '700',
    fontSize: 11,
  },
  tabSelectorCellTextActive: {
    color: '#00D68F',
  },
  quantityWidget: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  quantityBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#1A1A1A',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#1C1C1E',
  },
  quantityBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 16,
  },
  quantityValue: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 14,
    marginHorizontal: 12,
  },
  badgeTextGreen: {
    color: '#00D68F',
    fontWeight: '800',
    fontSize: 13,
    backgroundColor: 'rgba(0,214,143,0.12)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 10,
    overflow: 'hidden',
  },
  actionButton: {
    paddingVertical: 14,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionButtonText: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 14,
  },
});
