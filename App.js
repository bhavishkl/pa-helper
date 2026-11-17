import React from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const actions = [
  { icon: '✓', title: 'My tasks', subtitle: 'Keep track of what matters', color: '#E8F1FF' },
  { icon: '⌁', title: 'Quick notes', subtitle: 'Capture an idea instantly', color: '#F1EAFE' },
  { icon: '◷', title: 'Reminders', subtitle: 'Things to follow up on', color: '#E7F7EF' },
  { icon: '⚙', title: 'Settings', subtitle: 'Make the app yours', color: '#FFF2E5' },
];

function ActionCard({ item }) {
  return (
    <TouchableOpacity activeOpacity={0.75} style={styles.actionCard} accessibilityRole="button" accessibilityLabel={item.title}>
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
  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor="#F7F8FC" />
      <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View>
            <Text style={styles.eyebrow}>PERSONAL ASSISTANT</Text>
            <Text style={styles.greeting}>Good day 👋</Text>
            <Text style={styles.headerSubtitle}>What would you like to do?</Text>
          </View>
          <View style={styles.avatar}><Text style={styles.avatarText}>P</Text></View>
        </View>

        <View style={styles.hero}>
          <View style={styles.heroBadge}><Text style={styles.heroBadgeText}>YOUR DAY, IN CONTROL</Text></View>
          <Text style={styles.heroTitle}>A little more{ '\n' }organized.</Text>
          <Text style={styles.heroDescription}>Your personal space for tasks, notes and reminders — all in one place.</Text>
          <TouchableOpacity activeOpacity={0.85} style={styles.primaryButton} accessibilityRole="button">
            <Text style={styles.primaryButtonText}>Get started <Text style={styles.buttonArrow}>→</Text></Text>
          </TouchableOpacity>
          <View style={styles.heroDecoration} />
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
            <Text style={styles.tipTitle}>A fresh start</Text>
            <Text style={styles.tipText}>Your assistant is ready. Add a task or jot down a note to get going.</Text>
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
  heroDescription: { color: '#D5DFF2', fontSize: 13, lineHeight: 20, maxWidth: 255, marginTop: 9 },
  primaryButton: { alignSelf: 'flex-start', backgroundColor: '#FFFFFF', paddingHorizontal: 17, paddingVertical: 12, borderRadius: 13, marginTop: 19 },
  primaryButtonText: { color: '#243B70', fontSize: 13, fontWeight: '800' },
  buttonArrow: { fontSize: 16 },
  heroDecoration: { position: 'absolute', width: 170, height: 170, borderRadius: 85, borderWidth: 28, borderColor: 'rgba(255,255,255,0.06)', right: -62, bottom: -82 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 },
  sectionTitle: { color: '#17233B', fontSize: 19, fontWeight: '800' },
  sectionHint: { color: '#9AA3B2', fontSize: 9, fontWeight: '800', letterSpacing: 1.2 },
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
