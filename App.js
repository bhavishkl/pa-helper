import React, { useCallback, useEffect, useState } from 'react';
import {
  AppState, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text,
  TouchableOpacity, View, Alert, Platform
} from 'react-native';
import { requireNativeModule } from 'expo-modules-core';

const PAHelperNative = Platform.OS === 'android'
  ? requireNativeModule('PAHelperNative')
  : null;

const actions = [
  { icon: '✓', title: 'My tasks', subtitle: 'Keep track of what matters', color: '#E8F1FF' },
  { icon: '⌁', title: 'Quick notes', subtitle: 'Capture an idea instantly', color: '#F1EAFE' },
  { icon: '◷', title: 'Reminders', subtitle: 'Things to follow up on', color: '#E7F7EF' },
  { icon: '⚙', title: 'Settings', subtitle: 'Make the app yours', color: '#FFF2E5' },
];

function ActionCard({ item }) {
  return (
    <TouchableOpacity activeOpacity={0.75} style={styles.actionCard} accessibilityRole="button">
      <View style={[styles.actionIcon, { backgroundColor: item.color }]}>
        <Text style={styles.actionIconText}>{item.icon}</Text>
      </View>
      <View style={styles.actionCopy}>
        <Text style={styles.actionTitle}>{item.title}</Text>
        <Text style={styles.actionSubtitle}>{item.subtitle}</Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </TouchableOpacity>
  );
}

export default function App() {
  const [capture, setCapture] = useState({ text: '', capturedAt: 0, status: 'empty' });
  const [busy, setBusy] = useState(false);

  const refreshCapture = useCallback(async () => {
    if (!PAHelperNative) return;
    try {
      setCapture(await PAHelperNative.getLatestCapture());
    } catch (error) {
      Alert.alert('Could not load capture', error.message || 'Try again.');
    }
  }, []);

  useEffect(() => {
    refreshCapture();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') refreshCapture();
    });
    return () => subscription.remove();
  }, [refreshCapture]);

  const enableAssistant = async () => {
    if (!PAHelperNative) {
      Alert.alert('Android only', 'The volume shortcut is available in the Android app.');
      return;
    }
    try {
      setBusy(true);
      const result = await PAHelperNative.enableVolumeShortcut();
      if (result.status === 'accessibility_permission_required') {
        Alert.alert(
          'Enable PA Helper accessibility',
          'In Accessibility settings, open PA Helper screen capture and turn it on. This enables the global Volume Up shortcut and one-shot screenshots; PA Helper does not continuously capture or inspect screen content.'
        );
      } else if (result.status === 'shortcut_ready') {
        Alert.alert(
          'Volume shortcut ready',
          'When media is not playing, press Volume Up three times within 1.2 seconds to capture. If you press once or twice, normal volume adjustment is replayed. When media is playing, all volume presses work normally.'
        );
      }
    } catch (error) {
      Alert.alert('Could not enable shortcut', error.message || 'Check Accessibility settings and try again.');
    } finally {
      setBusy(false);
    }
  };

  const stopAssistant = async () => {
    try {
      await PAHelperNative?.stopFloatingAssistant();
      Alert.alert('PA Helper', 'Floating assistant stopped.');
    } catch (error) {
      Alert.alert('Could not stop assistant', error.message || 'Try again.');
    }
  };

  const statusLabel = {
    success: 'Text captured',
    no_text_found: 'No readable text found',
    ocr_failed: 'Text recognition failed',
    overlay_failed: 'Could not display floating button',
    accessibility_service_unavailable: 'Enable PA Helper accessibility',
    screenshot_request_failed: 'Could not request screenshot',
    screenshot_failed_1: 'Screenshot failed internally',
    screenshot_failed_2: 'Wait a moment before capturing again',
    screenshot_failed_3: 'Display unavailable',
    screenshot_failed_4: 'Window unavailable',
    screenshot_failed_5: 'Accessibility screenshot access unavailable',
    screenshot_failed_6: 'This screen blocks screenshots',
    capture_in_progress: 'Capture already in progress',
    image_processing_failed: 'Image processing failed',
    empty: 'Nothing captured yet'
  }[capture.status] || capture.status;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor="#F7F8FC" />
      <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View>
            <Text style={styles.eyebrow}>PERSONAL ASSISTANT</Text>
            <Text style={styles.greeting}>Good day 👋</Text>
            <Text style={styles.headerSubtitle}>Capture screen text without a floating button.</Text>
          </View>
          <View style={styles.avatar}><Text style={styles.avatarText}>P</Text></View>
        </View>

        <View style={styles.hero}>
          <View style={styles.heroBadge}><Text style={styles.heroBadgeText}>VOLUME SHORTCUT</Text></View>
          <Text style={styles.heroTitle}>Capture without{ '\n' }an overlay.</Text>
          <Text style={styles.heroDescription}>When no media is playing, press Volume Up three times within 1.2 seconds to capture one screenshot and read its text. If media is playing, volume keys work normally.</Text>
          <TouchableOpacity activeOpacity={0.85} style={styles.primaryButton} onPress={enableAssistant} disabled={busy}>
            <Text style={styles.primaryButtonText}>{busy ? 'Opening permissions…' : 'Enable volume shortcut  →'}</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Latest screen capture</Text>
          <Text style={styles.sectionHint}>{statusLabel.toUpperCase()}</Text>
        </View>
        <View style={styles.captureCard}>
          {capture.text ? (
            <>
              <Text style={styles.captureText}>{capture.text}</Text>
              <Text style={styles.captureMeta}>
                Captured {capture.capturedAt ? new Date(capture.capturedAt).toLocaleString() : ''}
              </Text>
            </>
          ) : (
            <Text style={styles.emptyText}>Your extracted text appears here after the Volume Up triple-press shortcut. Capture is on-demand only.</Text>
          )}
          <TouchableOpacity style={styles.secondaryButton} onPress={refreshCapture}>
            <Text style={styles.secondaryButtonText}>Refresh captured text</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Quick access</Text>
          <Text style={styles.sectionHint}>YOUR TOOLS</Text>
        </View>
        <View style={styles.actions}>
          {actions.map((item) => <ActionCard key={item.title} item={item} />)}
        </View>

        <View style={styles.tipCard}>
          <Text style={styles.tipIcon}>✦</Text>
          <View style={styles.tipCopy}>
            <Text style={styles.tipTitle}>Privacy by design</Text>
            <Text style={styles.tipText}>PA Helper captures only after the three-press shortcut. OCR runs on-device. Secure screens may still block screenshots.</Text>
          </View>
        </View>
        <Text style={styles.footer}>PA HELPER  •  MADE TO KEEP THINGS SIMPLE</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: '#F7F8FC' },
  container: { paddingHorizontal: 22, paddingTop: 22, paddingBottom: 30 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 },
  eyebrow: { color: '#718096', fontSize: 10, fontWeight: '800', letterSpacing: 1.8, marginBottom: 8 },
  greeting: { color: '#17233B', fontSize: 29, fontWeight: '800', letterSpacing: -0.8 },
  headerSubtitle: { color: '#7B8496', fontSize: 14, marginTop: 5 },
  avatar: { width: 46, height: 46, borderRadius: 16, backgroundColor: '#243B70', alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: '#FFFFFF', fontSize: 19, fontWeight: '800' },
  hero: { backgroundColor: '#243B70', borderRadius: 26, padding: 24, minHeight: 265, overflow: 'hidden', marginBottom: 30 },
  heroBadge: { alignSelf: 'flex-start', backgroundColor: 'rgba(255,255,255,0.13)', paddingHorizontal: 10, paddingVertical: 7, borderRadius: 20 },
  heroBadgeText: { color: '#D9E6FF', fontSize: 9, fontWeight: '800', letterSpacing: 1.2 },
  heroTitle: { color: '#FFFFFF', fontSize: 34, lineHeight: 39, fontWeight: '800', letterSpacing: -1, marginTop: 18 },
  heroDescription: { color: '#D5DFF2', fontSize: 13, lineHeight: 20, maxWidth: 270, marginTop: 9 },
  primaryButton: { alignSelf: 'flex-start', backgroundColor: '#FFFFFF', paddingHorizontal: 17, paddingVertical: 12, borderRadius: 13, marginTop: 19 },
  primaryButtonText: { color: '#243B70', fontSize: 12, fontWeight: '800' },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 },
  sectionTitle: { color: '#17233B', fontSize: 19, fontWeight: '800' },
  sectionHint: { color: '#9AA3B2', fontSize: 9, fontWeight: '800', letterSpacing: 1.1, maxWidth: 135, textAlign: 'right' },
  captureCard: { backgroundColor: '#FFFFFF', borderRadius: 17, padding: 16, borderWidth: 1, borderColor: '#EEF0F5', marginBottom: 28 },
  captureText: { color: '#273449', fontSize: 13, lineHeight: 20 },
  captureMeta: { color: '#9AA3B2', fontSize: 10, marginTop: 12 },
  emptyText: { color: '#8B93A2', fontSize: 12, lineHeight: 19 },
  secondaryButton: { backgroundColor: '#EEF3FC', padding: 12, borderRadius: 12, alignItems: 'center', marginTop: 14 },
  secondaryButtonText: { color: '#243B70', fontSize: 12, fontWeight: '800' },
  stopButton: { padding: 10, alignItems: 'center', marginTop: 5 },
  stopButtonText: { color: '#9B4B4B', fontSize: 11, fontWeight: '700' },
  actions: { gap: 10 },
  actionCard: { backgroundColor: '#FFFFFF', borderRadius: 17, padding: 14, flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: '#EEF0F5' },
  actionIcon: { width: 43, height: 43, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  actionIconText: { color: '#263B68', fontSize: 21, fontWeight: '700' },
  actionCopy: { flex: 1, marginLeft: 13 },
  actionTitle: { color: '#202B40', fontSize: 14, fontWeight: '800' },
  actionSubtitle: { color: '#8B93A2', fontSize: 11, marginTop: 4 },
  chevron: { color: '#AAB1BE', fontSize: 25, paddingHorizontal: 4 },
  tipCard: { flexDirection: 'row', alignItems: 'flex-start', backgroundColor: '#EEF3FC', borderRadius: 18, padding: 17, marginTop: 22 },
  tipIcon: { color: '#365A9A', fontSize: 21, marginRight: 12 },
  tipCopy: { flex: 1 },
  tipTitle: { color: '#263B68', fontSize: 13, fontWeight: '800', marginBottom: 5 },
  tipText: { color: '#65738B', fontSize: 12, lineHeight: 18 },
  footer: { color: '#AAB1BE', fontSize: 9, fontWeight: '700', letterSpacing: 1, textAlign: 'center', marginTop: 25 },
});
