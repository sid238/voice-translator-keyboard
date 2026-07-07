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
} from 'react-native';

const { FloatingBubble } = NativeModules;

export default function App() {
  const [isKeyboardEnabled, setIsKeyboardEnabled] = useState(false);
  const [isKeyboardDefault, setIsKeyboardDefault] = useState(false);
  const [isKeyboardVisible, setIsKeyboardVisible] = useState(false);
  const appState = useRef(AppState.currentState);

  // Bottom Nav Tab: 'home' | 'settings' | 'about'
  const [activeTab, setActiveTab] = useState('home');

  // Active Settings Section: null | string
  const [activeSection, setActiveSection] = useState(null);

  // Preference fields synced with native keyboard SharedPreferences
  const [theme, setTheme] = useState('red'); // Cyberpunk Red Default!
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [vibeEnabled, setVibeEnabled] = useState(true);
  const [numberRowEnabled, setNumberRowEnabled] = useState(true);
  const [keyboardHeight, setKeyboardHeight] = useState(270);
  const [gestureEnabled, setGestureEnabled] = useState(true);

  // Custom UI Spacing, Suggestions & delay settings
  const [keySpacing, setKeySpacing] = useState(4);
  const [longPressDelay, setLongPressDelay] = useState(400);
  const [suggestionsEnabled, setSuggestionsEnabled] = useState(true);
  const [autoCorrectEnabled, setAutoCorrectEnabled] = useState(true);

  // Other fields
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

    // Listen to soft keyboard show/hide events to hide bottom navigation and avoid overlaps
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
          const t = await FloatingBubble.getStringSetting('theme', 'red'); // Default theme red
          setTheme(t);
          const lang = await FloatingBubble.getStringSetting('selected_language', 'en');
          setSelectedLanguage(lang);
          const langsStr = await FloatingBubble.getStringSetting('selected_languages', 'en');
          const langsList = langsStr.split(',').filter(x => x.length > 0);
          setSelectedLanguages(langsList.length > 0 ? langsList : ['en']);
          const escale = await FloatingBubble.getStringSetting('emoji_scale', 'medium');
          setEmojiScale(escale);
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
        }
        if (FloatingBubble.getIntSetting) {
          const h = await FloatingBubble.getIntSetting('keyboard_height_dp', 270);
          setKeyboardHeight(h);
          const climit = await FloatingBubble.getIntSetting('clipboard_limit', 100);
          setClipboardLimit(climit);
          const plimit = await FloatingBubble.getIntSetting('pin_limit', 10);
          setPinLimit(plimit);
          const spacing = await FloatingBubble.getIntSetting('key_spacing_dp', 4);
          setKeySpacing(spacing);
          const delay = await FloatingBubble.getIntSetting('long_press_delay_ms', 400);
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

  // Dynamic Theme Colors
  const colors = {
    purple: {
      primary: '#A855F7',
      primaryDark: '#7C3AED',
      background: '#090514',
      card: '#130C24',
      text: '#F3E8FF',
      subtext: '#9887B0',
      border: '#281745',
      accent: '#D8B4FE',
    },
    dark: { // Default Pitch Black layout theme
      primary: '#FFFFFF',
      primaryDark: '#94A3B8',
      background: '#000000',
      card: '#0A0A0A',
      text: '#F8FAFC',
      subtext: '#888888',
      border: '#141414',
      accent: '#CBD5E1',
    },
    blue: {
      primary: '#3B82F6',
      primaryDark: '#1D4ED8',
      background: '#050B14',
      card: '#0C1524',
      text: '#EFF6FF',
      subtext: '#60A5FA',
      border: '#172554',
      accent: '#93C5FD',
    },
    red: {
      primary: '#FF0055',      // Cyberpunk Red
      primaryDark: '#B9003B',
      background: '#0B0B0F',   // Midnight Grey
      card: '#12131C',         // Midnight Slate Card
      text: '#F8FAFC',
      subtext: '#A1A1AA',
      border: '#2A101C',       // Subtle neon outline
      accent: '#FF3377',
    },
    green: {
      primary: '#10B981',
      primaryDark: '#047857',
      background: '#03120E',
      card: '#06261E',
      text: '#ECFDF5',
      subtext: '#34D399',
      border: '#064E3B',
      accent: '#A7F3D0',
    },
  }[theme] || {
    primary: '#FFFFFF',
    primaryDark: '#94A3B8',
    background: '#000000',
    card: '#0A0A0A',
    text: '#F8FAFC',
    subtext: '#888888',
    border: '#141414',
    accent: '#CBD5E1',
  };

  const localStyles = StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: colors.background,
      paddingTop: StatusBar.currentHeight || 0,
    },
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      paddingHorizontal: 20,
      paddingVertical: 16,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
      backgroundColor: colors.card,
    },
    headerTitle: {
      fontSize: 22,
      fontWeight: '900',
      color: colors.primary,
      letterSpacing: 1.2,
    },
    headerStatusDot: {
      width: 10,
      height: 10,
      borderRadius: 5,
    },
    scrollContent: {
      flex: 1,
      paddingHorizontal: 20,
      paddingTop: 16,
      paddingBottom: 40,
    },
    navBar: {
      flexDirection: 'row',
      height: 64,
      backgroundColor: colors.card,
      borderTopWidth: 1,
      borderTopColor: colors.border,
      justifyContent: 'space-around',
      alignItems: 'center',
    },
    navItem: {
      alignItems: 'center',
      justifyContent: 'center',
      flex: 1,
      height: '100%',
    },
    navText: {
      fontSize: 12,
      fontWeight: '600',
      marginTop: 4,
    },
    card: {
      backgroundColor: colors.card,
      borderColor: colors.border,
      borderWidth: 1,
      borderRadius: 16,
      padding: 18,
      marginBottom: 20,
    },
    cardTitle: {
      fontSize: 16,
      fontWeight: 'bold',
      color: colors.text,
      marginBottom: 12,
    },
    statusRow: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      marginVertical: 6,
    },
    statusLabel: {
      color: colors.subtext,
      fontSize: 14,
    },
    statusValue: {
      fontWeight: 'bold',
      fontSize: 14,
    },
    button: {
      borderRadius: 12,
      paddingVertical: 14,
      alignItems: 'center',
      justifyContent: 'center',
      marginVertical: 8,
    },
    buttonText: {
      color: colors.background === '#000000' ? '#FFFFFF' : '#FFFFFF',
      fontWeight: 'bold',
      fontSize: 15,
    },
    testCard: {
      backgroundColor: colors.card,
      borderColor: colors.border,
      borderWidth: 1,
      borderRadius: 16,
      padding: 16,
      marginBottom: 20,
    },
    textInput: {
      backgroundColor: colors.background,
      color: colors.text,
      borderRadius: 10,
      borderColor: colors.border,
      borderWidth: 1,
      paddingHorizontal: 12,
      paddingVertical: 10,
      fontSize: 14,
      height: 70,
      textAlignVertical: 'top',
      marginTop: 10,
    },
    settingItem: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      paddingVertical: 15,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    settingItemTitle: {
      fontSize: 15,
      fontWeight: '600',
      color: colors.text,
    },
    settingItemSubtitle: {
      fontSize: 12,
      color: colors.subtext,
      marginTop: 2,
    },
    backHeader: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 20,
      paddingVertical: 16,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    backHeaderText: {
      color: colors.primary,
      fontSize: 16,
      fontWeight: 'bold',
      marginLeft: 10,
    },
    sectionContent: {
      paddingHorizontal: 20,
      paddingTop: 16,
    },
    sectionTitle: {
      fontSize: 20,
      fontWeight: '800',
      color: colors.text,
      marginBottom: 10,
    },
    sectionDesc: {
      fontSize: 13,
      color: colors.subtext,
      lineHeight: 18,
      marginBottom: 24,
    },
    switchRow: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      paddingVertical: 14,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    switchLabel: {
      fontSize: 15,
      color: colors.text,
      fontWeight: '500',
    },
    themeGrid: {
      flexDirection: 'row',
      flexWrap: 'wrap',
      justifyContent: 'space-between',
    },
    themeCard: {
      width: '48%',
      height: 90,
      borderRadius: 12,
      padding: 12,
      marginBottom: 16,
      borderWidth: 2,
      justifyContent: 'space-between',
    },
    themeCardName: {
      color: '#FFFFFF',
      fontWeight: 'bold',
      fontSize: 14,
    },
    themeColorPill: {
      width: 24,
      height: 24,
      borderRadius: 12,
      borderWidth: 2,
      borderColor: '#FFFFFF',
    },
  });

  const renderSectionDetails = () => {
    let title = '';
    let desc = '';
    let content = null;

    switch (activeSection) {
      case 'language':
        title = 'Language & Layout';
        desc = 'Select multiple input languages and custom keyboard layouts to switch between.';
        content = (
          <View>
            {[
              { id: 'en', name: 'English QWERTY', flag: '🇬🇧' },
              { id: 'hi_phonetic', name: 'Hindi Phonetic (Hinglish)', flag: '🇮🇳' },
              { id: 'es', name: 'Spanish QWERTY', flag: '🇪🇸' },
              { id: 'fr', name: 'French AZERTY', flag: '🇫🇷' },
            ].map((lang) => {
              const isSelected = selectedLanguages.includes(lang.id);
              return (
                <TouchableOpacity
                  key={lang.id}
                  style={[
                    localStyles.settingItem,
                    { borderBottomColor: isSelected ? colors.primary : colors.border }
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
                    <Text style={{ fontSize: 20, marginRight: 12 }}>{lang.flag}</Text>
                    <Text style={[localStyles.settingItemTitle, { color: isSelected ? colors.primary : colors.text }]}>
                      {lang.name}
                    </Text>
                  </View>
                  {isSelected && (
                    <Text style={{ color: colors.primary, fontWeight: 'bold' }}>✓</Text>
                  )}
                </TouchableOpacity>
              );
            })}
          </View>
        );
        break;

      case 'theme':
        title = 'Theme Store';
        desc = 'Instantly apply premium color ways and glow profiles to match your mood.';
        content = (
          <View style={localStyles.themeGrid}>
            {[
              { id: 'dark', name: 'Pure Black', color: '#FFFFFF', bg: '#000000' },
              { id: 'purple', name: 'Neon Purple', color: '#A855F7', bg: '#090514' },
              { id: 'blue', name: 'Ocean Blue', color: '#3B82F6', bg: '#050B14' },
              { id: 'red', name: 'Sunset Red', color: '#EF4444', bg: '#140505' },
              { id: 'green', name: 'Emerald', color: '#10B981', bg: '#03120E' },
            ].map((t) => (
              <TouchableOpacity
                key={t.id}
                style={[
                  localStyles.themeCard,
                  {
                    backgroundColor: t.bg,
                    borderColor: theme === t.id ? colors.primary : colors.border,
                  }
                ]}
                onPress={() => {
                  setTheme(t.id);
                  saveStringPref('theme', t.id);
                }}
              >
                <Text style={localStyles.themeCardName}>{t.name}</Text>
              </TouchableOpacity>
            ))}
            <View style={{ width: '100%', marginTop: 24, padding: 16, backgroundColor: colors.card, borderRadius: 12, borderWidth: 1, borderColor: colors.border }}>
              <Text style={{ color: '#FFF', fontSize: 16, fontWeight: 'bold', marginBottom: 4 }}>Custom Wallpaper background</Text>
              <Text style={{ color: colors.subtext, fontSize: 12, marginBottom: 16 }}>Set your own background image/photo for the keyboard keys overlay.</Text>
              <View style={{ flexDirection: 'row', gap: 12 }}>
                <TouchableOpacity
                  style={{ flex: 1, backgroundColor: colors.primary, paddingVertical: 12, borderRadius: 8, alignItems: 'center' }}
                  onPress={async () => {
                    try {
                      if (FloatingBubble && FloatingBubble.pickThemeImage) {
                        const path = await FloatingBubble.pickThemeImage();
                        Alert.alert("Success", "Custom keyboard background image set!");
                      }
                    } catch (e) {
                      if (e.message !== "Image picking was cancelled") {
                        Alert.alert("Error", e.message || "Failed to pick image");
                      }
                    }
                  }}
                >
                  <Text style={{ color: '#FFF', fontWeight: '700' }}>Choose Photo</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={{ flex: 1, backgroundColor: '#EF4444', paddingVertical: 12, borderRadius: 8, alignItems: 'center' }}
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
                  <Text style={{ color: '#FFF', fontWeight: '700' }}>Remove Photo</Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        );
        break;

      case 'keyboard':
        title = 'Keyboard Options';
        desc = 'Customize structural heights, vibration strengths, keyspacing, and delays.';
        content = (
          <View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Hinted Number Row</Text>
              <Switch
                value={numberRowEnabled}
                onValueChange={(val) => {
                  setNumberRowEnabled(val);
                  saveBooleanPref('number_row_enabled', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Keypress Sound Feedback</Text>
              <Switch
                value={soundEnabled}
                onValueChange={(val) => {
                  setSoundEnabled(val);
                  saveBooleanPref('sound_enabled', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Keypress Haptic Vibration</Text>
              <Switch
                value={vibeEnabled}
                onValueChange={(val) => {
                  setVibeEnabled(val);
                  saveBooleanPref('vibration_enabled', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>

            {/* Key Spacing option slider */}
            <View style={{ marginTop: 20 }}>
              <Text style={localStyles.settingItemTitle}>Key Spacing (dp)</Text>
              <Text style={localStyles.settingItemSubtitle}>Current: {keySpacing} dp</Text>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 12 }}>
                {[2, 4, 6, 8, 10].map((item) => (
                  <TouchableOpacity
                    key={item}
                    style={{
                      paddingVertical: 10,
                      backgroundColor: keySpacing === item ? colors.primary : colors.card,
                      borderRadius: 8,
                      borderWidth: 1,
                      borderColor: colors.border,
                      flex: 1,
                      alignItems: 'center',
                      marginHorizontal: 3,
                    }}
                    onPress={() => {
                      setKeySpacing(item);
                      saveIntPref('key_spacing_dp', item);
                    }}
                  >
                    <Text style={{ color: keySpacing === item ? '#000' : '#FFF', fontWeight: 'bold', fontSize: 13 }}>{item}px</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {/* Long press delay settings */}
            <View style={{ marginTop: 20 }}>
              <Text style={localStyles.settingItemTitle}>Long Press Delay (ms)</Text>
              <Text style={localStyles.settingItemSubtitle}>Current: {longPressDelay} ms</Text>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 12 }}>
                {[100, 200, 300, 400, 500, 600].map((item) => (
                  <TouchableOpacity
                    key={item}
                    style={{
                      paddingVertical: 10,
                      backgroundColor: longPressDelay === item ? colors.primary : colors.card,
                      borderRadius: 8,
                      borderWidth: 1,
                      borderColor: colors.border,
                      flex: 1,
                      alignItems: 'center',
                      marginHorizontal: 2,
                    }}
                    onPress={() => {
                      setLongPressDelay(item);
                      saveIntPref('long_press_delay_ms', item);
                    }}
                  >
                    <Text style={{ color: longPressDelay === item ? '#000' : '#FFF', fontWeight: 'bold', fontSize: 12 }}>{item}ms</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </View>
        );
        break;

      case 'smartbar':
        title = 'Smartbar Toolbar';
        desc = 'Manage quick shortcut actions present directly above keyboard letter inputs.';
        content = (
          <View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Enable Suggestions & Corrections</Text>
              <Switch
                value={suggestionsEnabled}
                onValueChange={(val) => {
                  setSuggestionsEnabled(val);
                  saveBooleanPref('suggestions_enabled', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Auto Correction (Word replacement)</Text>
              <Switch
                value={autoCorrectEnabled}
                onValueChange={(val) => {
                  setAutoCorrectEnabled(val);
                  saveBooleanPref('auto_correct_enabled', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
          </View>
        );
        break;

      case 'typing':
        title = 'Typing Corrections';
        desc = 'Adjust auto-corrections, spacebar actions, capitalization, and dictionary mappings.';
        content = (
          <View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Auto Capitalization</Text>
              <Switch
                value={autoCap}
                onValueChange={(val) => {
                  setAutoCap(val);
                  saveBooleanPref('auto_cap', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Double-Space Period (".")</Text>
              <Switch
                value={doubleSpacePeriod}
                onValueChange={(val) => {
                  setDoubleSpacePeriod(val);
                  saveBooleanPref('double_space_period', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
          </View>
        );
        break;

      case 'gesture':
        title = 'Gesture & NeoType';
        desc = 'Slide your finger over letters to write seamlessly without raising the hand.';
        content = (
          <View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Enable Glide Typing</Text>
              <Switch
                value={gestureEnabled}
                onValueChange={(val) => {
                  setGestureEnabled(val);
                  saveBooleanPref('gesture_enabled', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
          </View>
        );
        break;

      case 'clipboard':
        title = 'Clipboard Manager';
        desc = 'Configure copied item retention limits and prevent deletions.';
        content = (
          <View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Copied History Limit</Text>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <TouchableOpacity onPress={() => {
                  const n = Math.max(50, clipboardLimit - 50);
                  setClipboardLimit(n);
                  saveIntPref('clipboard_limit', n);
                }} style={{ padding: 8, backgroundColor: colors.border, borderRadius: 6 }}>
                  <Text style={{ color: '#FFF', fontWeight: 'bold' }}>-</Text>
                </TouchableOpacity>
                <Text style={{ color: colors.text, marginHorizontal: 12, fontWeight: 'bold' }}>{clipboardLimit}</Text>
                <TouchableOpacity onPress={() => {
                  const n = Math.min(250, clipboardLimit + 50);
                  setClipboardLimit(n);
                  saveIntPref('clipboard_limit', n);
                }} style={{ padding: 8, backgroundColor: colors.border, borderRadius: 6 }}>
                  <Text style={{ color: '#FFF', fontWeight: 'bold' }}>+</Text>
                </TouchableOpacity>
              </View>
            </View>
            <View style={localStyles.switchRow}>
              <Text style={localStyles.switchLabel}>Max Pins Limit</Text>
              <Text style={{ color: colors.primary, fontWeight: 'bold', fontSize: 15 }}>{pinLimit} Items</Text>
            </View>
          </View>
        );
        break;

      case 'emojis':
        title = 'Emoji Settings';
        desc = 'Choose layout sizes and category layouts for quick symbol inputs.';
        content = (
          <View>
            <Text style={[localStyles.settingItemTitle, { marginBottom: 12 }]}>Emoji Grid Scale</Text>
            {['small', 'medium', 'large'].map((scale) => (
              <TouchableOpacity
                key={scale}
                style={[
                  localStyles.settingItem,
                  { borderBottomColor: emojiScale === scale ? colors.primary : colors.border }
                ]}
                onPress={() => {
                  setEmojiScale(scale);
                  saveStringPref('emoji_scale', scale);
                }}
              >
                <Text style={[localStyles.settingItemTitle, { textTransform: 'capitalize', color: emojiScale === scale ? colors.primary : colors.text }]}>
                  {scale} Size Grid
                </Text>
                {emojiScale === scale && <Text style={{ color: colors.primary, fontWeight: 'bold' }}>✓</Text>}
              </TouchableOpacity>
            ))}
          </View>
        );
        break;

      case 'addons':
        title = 'Addons & Extensions';
        desc = 'Integrate third-party services like instant languages translator and speech recognition.';
        content = (
          <View>
            <View style={localStyles.switchRow}>
              <View style={{ flex: 1, marginRight: 8 }}>
                <Text style={localStyles.switchLabel}>Voice Dictation Engine</Text>
                <Text style={localStyles.settingItemSubtitle}>Convert spoken word to text instantly</Text>
              </View>
              <Switch
                value={addonVoiceText}
                onValueChange={(val) => {
                  setAddonVoiceText(val);
                  saveBooleanPref('addon_voice_text', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
            <View style={localStyles.switchRow}>
              <View style={{ flex: 1, marginRight: 8 }}>
                <Text style={localStyles.switchLabel}>Google Translate Integration</Text>
                <Text style={localStyles.settingItemSubtitle}>Translate written text in real time</Text>
              </View>
              <Switch
                value={addonTranslate}
                onValueChange={(val) => {
                  setAddonTranslate(val);
                  saveBooleanPref('addon_translate', val);
                }}
                trackColor={{ true: colors.primary }}
              />
            </View>
          </View>
        );
        break;

      case 'other':
        title = 'Other Settings';
        desc = 'Advanced backup, configuration wipes, and developer troubleshooting keys.';
        content = (
          <View>
            <TouchableOpacity
              style={[localStyles.button, { backgroundColor: colors.border }]}
              onPress={() => {
                alert('Preferences backup created successfully!');
              }}
            >
              <Text style={localStyles.buttonText}>Backup Settings Profile</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[localStyles.button, { backgroundColor: '#EF4444' }]}
              onPress={() => {
                setTheme('dark');
                setSoundEnabled(false);
                setVibeEnabled(true);
                setNumberRowEnabled(true);
                setKeyboardHeight(270);
                setKeySpacing(4);
                setLongPressDelay(400);
                saveStringPref('theme', 'dark');
                saveBooleanPref('sound_enabled', false);
                saveBooleanPref('vibration_enabled', true);
                saveBooleanPref('number_row_enabled', true);
                saveIntPref('keyboard_height_dp', 270);
                saveIntPref('key_spacing_dp', 4);
                saveIntPref('long_press_delay_ms', 400);
                alert('All settings reset to default values.');
              }}
            >
              <Text style={localStyles.buttonText}>Wipe Settings & Reset All</Text>
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
          style={localStyles.backHeader}
          onPress={() => setActiveSection(null)}
        >
          <Text style={{ color: colors.primary, fontSize: 20 }}>←</Text>
          <Text style={localStyles.backHeaderText}>Back to Settings</Text>
        </TouchableOpacity>
        <ScrollView style={localStyles.sectionContent}>
          <Text style={localStyles.sectionTitle}>{title}</Text>
          <Text style={localStyles.sectionDesc}>{desc}</Text>
          {content}
        </ScrollView>
      </View>
    );
  };

  // Render Home Tab
  const renderHome = () => {
    return (
      <ScrollView style={localStyles.scrollContent}>
        {/* Status wizard card */}
        <View style={localStyles.card}>
          <Text style={localStyles.cardTitle}>Keyboard Wizard Status</Text>
          <View style={localStyles.statusRow}>
            <Text style={localStyles.statusLabel}>Enabled in Settings:</Text>
            <Text
              style={[
                localStyles.statusValue,
                { color: isKeyboardEnabled ? '#10B981' : '#EF4444' }
              ]}
            >
              {isKeyboardEnabled ? 'ENABLED' : 'DISABLED'}
            </Text>
          </View>
          <View style={localStyles.statusRow}>
            <Text style={localStyles.statusLabel}>Active Default Input:</Text>
            <Text
              style={[
                localStyles.statusValue,
                { color: isKeyboardDefault ? '#10B981' : '#F59E0B' }
              ]}
            >
              {isKeyboardDefault ? 'ACTIVE' : 'INACTIVE'}
            </Text>
          </View>
        </View>

        {/* Dynamic Action Buttons */}
        {!isKeyboardEnabled && (
          <TouchableOpacity
            style={[localStyles.button, { backgroundColor: '#F59E0B' }]}
            onPress={openKeyboardSettings}
          >
            <Text style={localStyles.buttonText}>1. Enable Orbit Keyboard</Text>
          </TouchableOpacity>
        )}

        {isKeyboardEnabled && !isKeyboardDefault && (
          <TouchableOpacity
            style={[localStyles.button, { backgroundColor: colors.primary }]}
            onPress={selectKeyboard}
          >
            <Text style={localStyles.buttonText}>2. Select Orbit Keyboard</Text>
          </TouchableOpacity>
        )}

        {isKeyboardDefault && (
          <TouchableOpacity
            style={[localStyles.button, { backgroundColor: '#10B981' }]}
            onPress={selectKeyboard}
          >
            <Text style={localStyles.buttonText}>Switch / Disable Keyboard</Text>
          </TouchableOpacity>
        )}

        {/* Test interactive area */}
        <View style={localStyles.testCard}>
          <Text style={localStyles.cardTitle}>Test Your Overhauled Keyboard</Text>
          <Text style={{ fontSize: 12, color: colors.subtext, marginBottom: 8 }}>
            Tap the input field below to test settings like customizable keyspacing, long press delays, double-tap shift lock, word suggestions, and drag resizing handles.
          </Text>
          <TextInput
            style={localStyles.textInput}
            placeholder="Tap here to test NeoType input..."
            placeholderTextColor={colors.subtext}
            multiline
          />
        </View>

        {/* Keyboard Quick stats card */}
        <View style={localStyles.card}>
          <Text style={localStyles.cardTitle}>Engine Specs</Text>
          <Text style={{ color: colors.subtext, fontSize: 13, lineHeight: 18 }}>
            • <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>SVG Code Icons</Text>: Special keys render beautiful vector paths directly on the canvas without raw text or emojis.{"\n"}
            • <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Gesture Resize Mode</Text>: Tap "↕" in the toolbar and drag handles on the sides/top to change height and margins on the fly.{"\n"}
            • <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Auto Correct & Word Suggestions</Text>: Live prefix indexing highlights vocabulary recommendations in real-time.
          </Text>
        </View>
      </ScrollView>
    );
  };

  // Render Main Settings Page
  const renderSettings = () => {
    if (activeSection) {
      return renderSectionDetails();
    }

    const sections = [
      { id: 'language', title: 'Language & Layout', subtitle: 'Phonetic input mappings & system layout', symbol: '🌐' },
      { id: 'theme', title: 'Theme Store', subtitle: 'Change glow presets, accent colors, and background styles', symbol: '🎨' },
      { id: 'keyboard', title: 'Keyboard Settings', subtitle: 'Configure key spacing, delays, haptics, and row options', symbol: '⌨️' },
      { id: 'smartbar', title: 'Smartbar Configurations', subtitle: 'Choose buttons to place on the keyboard top bar', symbol: '🚀' },
      { id: 'typing', title: 'Typing & Corrections', subtitle: 'Configure auto-capitalizations, spacing, and suggest terms', symbol: '✍️' },
      { id: 'clipboard', title: 'Clipboard History', subtitle: 'Adjust item retention, pinned storage caps, and clipboard actions', symbol: '📋' },
      { id: 'addons', title: 'Addons & Extensions', subtitle: 'Integrate dictation speech tools and offline translators', symbol: '🧩' },
      { id: 'other', title: 'Other Settings', subtitle: 'Manage configuration backups, data wipes, and defaults', symbol: '⚙️' },
    ];

    return (
      <ScrollView style={localStyles.scrollContent}>
        <Text style={{ fontSize: 18, fontWeight: 'bold', color: colors.text, marginBottom: 16 }}>
          Control Panel Mappings
        </Text>
        {sections.map((sec) => (
          <TouchableOpacity
            key={sec.id}
            style={localStyles.settingItem}
            onPress={() => setActiveSection(sec.id)}
          >
            <View style={{ flexDirection: 'row', alignItems: 'center', flex: 1 }}>
              <Text style={{ fontSize: 20, marginRight: 16 }}>{sec.symbol}</Text>
              <View style={{ flex: 1 }}>
                <Text style={localStyles.settingItemTitle}>{sec.title}</Text>
                <Text style={localStyles.settingItemSubtitle}>{sec.subtitle}</Text>
              </View>
            </View>
            <Text style={{ color: colors.primary, fontWeight: 'bold', fontSize: 16 }}>→</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    );
  };

  // Render About & Help Page
  const renderAbout = () => {
    return (
      <ScrollView style={localStyles.scrollContent}>
        <View style={localStyles.card}>
          <Text style={[localStyles.cardTitle, { color: colors.primary, fontSize: 18 }]}>Orbit Keyboard</Text>
          <Text style={{ color: colors.subtext, fontSize: 13, marginBottom: 12 }}>
            Premium Android keyboard service configured with custom React Native settings.
          </Text>
          <Text style={{ color: colors.text, fontSize: 13, fontWeight: 'bold' }}>
            Version: 2.1.0-premium
          </Text>
          <Text style={{ color: colors.text, fontSize: 13, fontWeight: 'bold', marginTop: 4 }}>
            API Status: SharedPreferences Connected
          </Text>
        </View>

        <View style={localStyles.card}>
          <Text style={localStyles.cardTitle}>Guide & Mappings</Text>
          <Text style={{ color: colors.subtext, fontSize: 13, lineHeight: 18, marginBottom: 8 }}>
            1. Double tap the <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Shift Arrow (⇧)</Text> to enable Caps Lock. Single tap to type a single capital letter.
          </Text>
          <Text style={{ color: colors.subtext, fontSize: 13, lineHeight: 18, marginBottom: 8 }}>
            2. Drag borders in <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Resize Pane (↕)</Text> to change keyboard height and margins. Tap Save to apply.
          </Text>
          <Text style={{ color: colors.subtext, fontSize: 13, lineHeight: 18, marginBottom: 8 }}>
            3. Tap <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Clipboard (📋)</Text> on keyboard toolbar to access copied clipboard logs. Tap pin to save, or X to wipe.
          </Text>
          <Text style={{ color: colors.subtext, fontSize: 13, lineHeight: 18 }}>
            4. Tap <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Smiley (☺)</Text> on keyboard toolbar to open the multi-category emoji panel.
          </Text>
        </View>

        <View style={{ alignItems: 'center', marginTop: 20 }}>
          <Text style={{ color: colors.subtext, fontSize: 11 }}>
            © 2026 NeoType Inc. All rights reserved.
          </Text>
        </View>
      </ScrollView>
    );
  };

  return (
    <SafeAreaView style={localStyles.container}>
      <StatusBar barStyle="light-content" backgroundColor={colors.card} />

      {/* Header */}
      <View style={localStyles.header}>
        <Text style={localStyles.headerTitle}>Orbit</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <Text style={{ color: colors.subtext, fontSize: 12, marginRight: 8 }}>
            {isKeyboardDefault ? 'Active Default' : 'Setup Required'}
          </Text>
          <View
            style={[
              localStyles.headerStatusDot,
              { backgroundColor: isKeyboardDefault ? '#10B981' : '#F59E0B' }
            ]}
          />
        </View>
      </View>

      {/* Main Tab Render */}
      <View style={{ flex: 1 }}>
        {activeTab === 'home' && renderHome()}
        {activeTab === 'settings' && renderSettings()}
        {activeTab === 'about' && renderAbout()}
      </View>

      {/* Bottom Navigation (Hidden when keyboard is visible to avoid overlaps) */}
      {!isKeyboardVisible && (
        <View style={localStyles.navBar}>
          <TouchableOpacity
            style={localStyles.navItem}
            onPress={() => {
              setActiveTab('home');
              setActiveSection(null);
            }}
          >
            <Text style={{ fontSize: 18, color: activeTab === 'home' ? colors.primary : colors.subtext }}>🏠</Text>
            <Text style={[localStyles.navText, { color: activeTab === 'home' ? colors.primary : colors.subtext }]}>
              Home
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={localStyles.navItem}
            onPress={() => {
              setActiveTab('settings');
              setActiveSection(null);
            }}
          >
            <Text style={{ fontSize: 18, color: activeTab === 'settings' ? colors.primary : colors.subtext }}>⚙️</Text>
            <Text style={[localStyles.navText, { color: activeTab === 'settings' ? colors.primary : colors.subtext }]}>
              Settings
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={localStyles.navItem}
            onPress={() => {
              setActiveTab('about');
              setActiveSection(null);
            }}
          >
            <Text style={{ fontSize: 18, color: activeTab === 'about' ? colors.primary : colors.subtext }}>ℹ️</Text>
            <Text style={[localStyles.navText, { color: activeTab === 'about' ? colors.primary : colors.subtext }]}>
              About
            </Text>
          </TouchableOpacity>
        </View>
      )}
    </SafeAreaView>
  );
}
