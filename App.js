import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AppState, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text,
  TextInput, TouchableOpacity, View, Alert, Platform
} from 'react-native';
import { requireNativeModule } from 'expo-modules-core';

const PAHelperNative = Platform.OS === 'android'
  ? requireNativeModule('PAHelperNative')
  : null;

const formatMoney = (value) => '₹' + Number(value || 0).toLocaleString('en-IN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

export default function App() {
  const [tab, setTab] = useState('home');
  const [capture, setCapture] = useState({ text: '', capturedAt: 0, status: 'empty' });
  const [busy, setBusy] = useState(false);

  const [accounts, setAccounts] = useState([]);
  const [accountsLoaded, setAccountsLoaded] = useState(false);
  const [accountName, setAccountName] = useState('');
  const [openingBalance, setOpeningBalance] = useState('0');
  const [selectedAccountId, setSelectedAccountId] = useState(null);
  const [transactionAmount, setTransactionAmount] = useState('');
  const [transactionNote, setTransactionNote] = useState('');

  const selectedAccount = useMemo(
    () => accounts.find((account) => account.id === selectedAccountId) || null,
    [accounts, selectedAccountId]
  );

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

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      try {
        if (PAHelperNative) {
          const saved = await PAHelperNative.getAccounts();
          const parsed = JSON.parse(saved || '[]');
          if (mounted) {
            setAccounts(Array.isArray(parsed) ? parsed : []);
            setSelectedAccountId(parsed?.[0]?.id || null);
          }
        }
      } catch (error) {
        Alert.alert('Could not load accounts', error.message || 'Local account data could not be read.');
      } finally {
        if (mounted) setAccountsLoaded(true);
      }
    };
    load();
    return () => { mounted = false; };
  }, []);

  useEffect(() => {
    if (!accountsLoaded || !PAHelperNative) return;
    PAHelperNative.saveAccounts(JSON.stringify(accounts)).catch((error) => {
      Alert.alert('Could not save accounts', error.message || 'Your changes were not saved.');
    });
  }, [accounts, accountsLoaded]);

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
          'When media is not playing, press Volume Up twice within 1.2 seconds to capture one screenshot. A single press still changes volume after a short delay. When media is playing, volume keys work normally.'
        );
      }
    } catch (error) {
      Alert.alert('Could not enable shortcut', error.message || 'Check Accessibility settings and try again.');
    } finally {
      setBusy(false);
    }
  };

  const addAccount = () => {
    const name = accountName.trim();
    const balance = Number(openingBalance);
    if (!name) {
      Alert.alert('Account name required', 'Enter a name for this account.');
      return;
    }
    if (!Number.isFinite(balance) || balance < 0) {
      Alert.alert('Invalid balance', 'Enter a valid opening balance (zero or more).');
      return;
    }
    const id = String(Date.now()) + '-' + Math.random().toString(36).slice(2, 8);
    const account = {
      id,
      name,
      balance: Math.round(balance * 100) / 100,
      createdAt: Date.now(),
      transactions: balance > 0 ? [{
        id: id + '-opening',
        type: 'opening',
        amount: Math.round(balance * 100) / 100,
        note: 'Opening balance',
        createdAt: Date.now(),
        balanceAfter: Math.round(balance * 100) / 100
      }] : []
    };
    setAccounts((current) => [account, ...current]);
    setSelectedAccountId(id);
    setAccountName('');
    setOpeningBalance('0');
  };

  const applyTransaction = (type) => {
    const amount = Number(transactionAmount);
    if (!selectedAccount) {
      Alert.alert('Select an account', 'Choose an account before adding a transaction.');
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      Alert.alert('Invalid amount', 'Enter an amount greater than zero.');
      return;
    }
    const normalizedAmount = Math.round(amount * 100) / 100;
    setAccounts((current) => current.map((account) => {
      if (account.id !== selectedAccount.id) return account;
      const nextBalance = Math.round((account.balance + (type === 'add' ? normalizedAmount : -normalizedAmount)) * 100) / 100;
      const transaction = {
        id: String(Date.now()) + '-' + Math.random().toString(36).slice(2, 7),
        type,
        amount: normalizedAmount,
        note: transactionNote.trim(),
        createdAt: Date.now(),
        balanceAfter: nextBalance
      };
      return {
        ...account,
        balance: nextBalance,
        transactions: [transaction, ...(account.transactions || [])]
      };
    }));
    setTransactionAmount('');
    setTransactionNote('');
  };

  const statusLabel = {
    success: 'Text captured',
    no_text_found: 'No readable text found',
    ocr_failed: 'Text recognition failed',
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

  const renderHome = () => (
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
        <Text style={styles.heroDescription}>When no media is playing, press Volume Up twice within 1.2 seconds to capture one screenshot and read its text. If media is playing, volume keys work normally.</Text>
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
            <Text style={styles.captureMeta}>Captured {capture.capturedAt ? new Date(capture.capturedAt).toLocaleString() : ''}</Text>
          </>
        ) : (
          <Text style={styles.emptyText}>Your extracted text appears here after the Volume Up double-press shortcut. Capture is on-demand only.</Text>
        )}
        <TouchableOpacity style={styles.secondaryButton} onPress={refreshCapture}>
          <Text style={styles.secondaryButtonText}>Refresh captured text</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.tipCard}>
        <Text style={styles.tipIcon}>✦</Text>
        <View style={styles.tipCopy}>
          <Text style={styles.tipTitle}>Privacy by design</Text>
          <Text style={styles.tipText}>PA Helper captures only after the two-press shortcut. OCR runs on-device. Secure screens may still block screenshots.</Text>
        </View>
      </View>
      <Text style={styles.footer}>PA HELPER  •  MADE TO KEEP THINGS SIMPLE</Text>
    </ScrollView>
  );

  const renderAccounts = () => (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <View style={styles.pageHeading}>
        <Text style={styles.eyebrow}>YOUR MONEY</Text>
        <Text style={styles.pageTitle}>Accounts</Text>
        <Text style={styles.headerSubtitle}>Track balances and every addition or deduction.</Text>
      </View>

      <View style={styles.totalCard}>
        <Text style={styles.totalLabel}>COMBINED BALANCE</Text>
        <Text style={styles.totalValue}>{formatMoney(accounts.reduce((sum, item) => sum + Number(item.balance || 0), 0))}</Text>
        <Text style={styles.totalFoot}>{accounts.length} {accounts.length === 1 ? 'account' : 'accounts'} stored on this device</Text>
      </View>

      <View style={styles.formCard}>
        <Text style={styles.cardTitle}>Create account</Text>
        <Text style={styles.fieldLabel}>ACCOUNT NAME</Text>
        <TextInput
          value={accountName}
          onChangeText={setAccountName}
          placeholder="e.g. Cash, Bank, Santosh"
          placeholderTextColor="#9AA3B2"
          style={styles.input}
          maxLength={40}
        />
        <Text style={styles.fieldLabel}>OPENING BALANCE (₹)</Text>
        <TextInput
          value={openingBalance}
          onChangeText={setOpeningBalance}
          placeholder="0.00"
          placeholderTextColor="#9AA3B2"
          keyboardType="decimal-pad"
          style={styles.input}
        />
        <TouchableOpacity style={styles.primaryWideButton} onPress={addAccount}>
          <Text style={styles.primaryWideButtonText}>＋  Add account</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Your accounts</Text>
        <Text style={styles.sectionHint}>TAP TO SELECT</Text>
      </View>
      {accounts.length === 0 ? (
        <View style={styles.emptyAccounts}>
          <Text style={styles.emptyTitle}>No accounts yet</Text>
          <Text style={styles.emptyText}>Create your first account above. You can add as many as you need.</Text>
        </View>
      ) : accounts.map((account) => (
        <TouchableOpacity
          key={account.id}
          activeOpacity={0.8}
          onPress={() => setSelectedAccountId(account.id)}
          style={[styles.accountCard, selectedAccountId === account.id && styles.accountCardSelected]}
        >
          <View style={styles.accountTopRow}>
            <View style={styles.accountIcon}><Text style={styles.accountIconText}>{(account.name || '?').slice(0, 1).toUpperCase()}</Text></View>
            <View style={styles.accountNameWrap}>
              <Text style={styles.accountName}>{account.name}</Text>
              <Text style={styles.accountMeta}>{(account.transactions || []).length} entries</Text>
            </View>
            <Text style={styles.accountBalance}>{formatMoney(account.balance)}</Text>
          </View>
          {selectedAccountId === account.id ? <Text style={styles.selectedLabel}>SELECTED ACCOUNT</Text> : null}
        </TouchableOpacity>
      ))}

      {selectedAccount ? (
        <View style={styles.formCard}>
          <Text style={styles.cardTitle}>Update {selectedAccount.name}</Text>
          <Text style={styles.currentBalance}>Current balance: {formatMoney(selectedAccount.balance)}</Text>
          <Text style={styles.fieldLabel}>AMOUNT (₹)</Text>
          <TextInput
            value={transactionAmount}
            onChangeText={setTransactionAmount}
            placeholder="Enter amount"
            placeholderTextColor="#9AA3B2"
            keyboardType="decimal-pad"
            style={styles.input}
          />
          <Text style={styles.fieldLabel}>NOTE (OPTIONAL)</Text>
          <TextInput
            value={transactionNote}
            onChangeText={setTransactionNote}
            placeholder="e.g. Received payment, groceries"
            placeholderTextColor="#9AA3B2"
            style={styles.input}
            maxLength={80}
          />
          <View style={styles.transactionButtons}>
            <TouchableOpacity style={[styles.transactionButton, styles.addButton]} onPress={() => applyTransaction('add')}>
              <Text style={styles.transactionButtonText}>＋  Add amount</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.transactionButton, styles.minusButton]} onPress={() => applyTransaction('minus')}>
              <Text style={styles.transactionButtonText}>−  Minus amount</Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : null}

      {selectedAccount && (selectedAccount.transactions || []).length > 0 ? (
        <View style={styles.historyCard}>
          <Text style={styles.cardTitle}>Recent entries · {selectedAccount.name}</Text>
          {selectedAccount.transactions.map((entry) => (
            <View key={entry.id} style={styles.historyRow}>
              <View style={styles.historyCopy}>
                <Text style={styles.historyTitle}>{entry.note || (entry.type === 'opening' ? 'Opening balance' : entry.type === 'add' ? 'Added' : 'Deducted')}</Text>
                <Text style={styles.historyDate}>{new Date(entry.createdAt).toLocaleString()}</Text>
              </View>
              <View style={styles.historyAmountWrap}>
                <Text style={[styles.historyAmount, entry.type === 'minus' && styles.negativeAmount]}>
                  {entry.type === 'minus' ? '− ' : '+ '}{formatMoney(entry.amount)}
                </Text>
                <Text style={styles.historyBalance}>Bal. {formatMoney(entry.balanceAfter)}</Text>
              </View>
            </View>
          ))}
        </View>
      ) : null}
      <Text style={styles.footer}>ACCOUNT DATA STAYS ON THIS DEVICE</Text>
    </ScrollView>
  );

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor="#F7F8FC" />
      <View style={styles.screen}>
        <View style={styles.content}>{tab === 'home' ? renderHome() : renderAccounts()}</View>
        <View style={styles.bottomNav}>
          <TouchableOpacity style={styles.navItem} onPress={() => setTab('home')}>
            <Text style={[styles.navIcon, tab === 'home' && styles.navActive]}>⌂</Text>
            <Text style={[styles.navLabel, tab === 'home' && styles.navActive]}>Home</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.navItem} onPress={() => setTab('accounts')}>
            <Text style={[styles.navIcon, tab === 'accounts' && styles.navActive]}>▤</Text>
            <Text style={[styles.navLabel, tab === 'accounts' && styles.navActive]}>Accounts</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: '#F7F8FC' },
  screen: { flex: 1 },
  content: { flex: 1 },
  container: { paddingHorizontal: 22, paddingTop: 22, paddingBottom: 28 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 },
  eyebrow: { color: '#718096', fontSize: 10, fontWeight: '800', letterSpacing: 1.8, marginBottom: 8 },
  greeting: { color: '#17233B', fontSize: 29, fontWeight: '800', letterSpacing: -0.8 },
  headerSubtitle: { color: '#7B8496', fontSize: 14, marginTop: 5 },
  avatar: { width: 46, height: 46, borderRadius: 16, backgroundColor: '#243B70', alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: '#FFFFFF', fontSize: 19, fontWeight: '800' },
  hero: { backgroundColor: '#243B70', borderRadius: 26, padding: 24, minHeight: 260, overflow: 'hidden', marginBottom: 28 },
  heroBadge: { alignSelf: 'flex-start', backgroundColor: 'rgba(255,255,255,0.13)', paddingHorizontal: 10, paddingVertical: 7, borderRadius: 20 },
  heroBadgeText: { color: '#D9E6FF', fontSize: 9, fontWeight: '800', letterSpacing: 1.2 },
  heroTitle: { color: '#FFFFFF', fontSize: 34, lineHeight: 39, fontWeight: '800', letterSpacing: -1, marginTop: 18 },
  heroDescription: { color: '#D5DFF2', fontSize: 13, lineHeight: 20, maxWidth: 290, marginTop: 9 },
  primaryButton: { alignSelf: 'flex-start', backgroundColor: '#FFFFFF', paddingHorizontal: 17, paddingVertical: 12, borderRadius: 13, marginTop: 19 },
  primaryButtonText: { color: '#243B70', fontSize: 12, fontWeight: '800' },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14, marginTop: 8 },
  sectionTitle: { color: '#17233B', fontSize: 19, fontWeight: '800' },
  sectionHint: { color: '#9AA3B2', fontSize: 9, fontWeight: '800', letterSpacing: 1.1, maxWidth: 135, textAlign: 'right' },
  captureCard: { backgroundColor: '#FFFFFF', borderRadius: 17, padding: 16, borderWidth: 1, borderColor: '#EEF0F5', marginBottom: 22 },
  captureText: { color: '#273449', fontSize: 13, lineHeight: 20 },
  captureMeta: { color: '#9AA3B2', fontSize: 10, marginTop: 12 },
  emptyText: { color: '#8B93A2', fontSize: 12, lineHeight: 19 },
  secondaryButton: { backgroundColor: '#EEF3FC', padding: 12, borderRadius: 12, alignItems: 'center', marginTop: 14 },
  secondaryButtonText: { color: '#243B70', fontSize: 12, fontWeight: '800' },
  tipCard: { flexDirection: 'row', alignItems: 'flex-start', backgroundColor: '#EEF3FC', borderRadius: 18, padding: 17, marginTop: 8 },
  tipIcon: { color: '#365A9A', fontSize: 21, marginRight: 12 },
  tipCopy: { flex: 1 },
  tipTitle: { color: '#263B68', fontSize: 13, fontWeight: '800', marginBottom: 5 },
  tipText: { color: '#65738B', fontSize: 12, lineHeight: 18 },
  pageHeading: { marginBottom: 18 },
  pageTitle: { color: '#17233B', fontSize: 30, fontWeight: '800', letterSpacing: -0.8 },
  totalCard: { backgroundColor: '#243B70', borderRadius: 22, padding: 20, marginBottom: 18 },
  totalLabel: { color: '#D5DFF2', fontSize: 10, fontWeight: '800', letterSpacing: 1.3 },
  totalValue: { color: '#FFFFFF', fontSize: 29, fontWeight: '800', marginTop: 9 },
  totalFoot: { color: '#D5DFF2', fontSize: 11, marginTop: 5 },
  formCard: { backgroundColor: '#FFFFFF', borderRadius: 18, padding: 17, borderWidth: 1, borderColor: '#EEF0F5', marginBottom: 20 },
  cardTitle: { color: '#202B40', fontSize: 16, fontWeight: '800', marginBottom: 12 },
  fieldLabel: { color: '#718096', fontSize: 9, fontWeight: '800', letterSpacing: 1, marginTop: 10, marginBottom: 7 },
  input: { backgroundColor: '#F7F8FC', borderWidth: 1, borderColor: '#E8ECF3', borderRadius: 12, paddingHorizontal: 13, paddingVertical: 12, color: '#202B40', fontSize: 14 },
  primaryWideButton: { backgroundColor: '#243B70', borderRadius: 12, padding: 14, alignItems: 'center', marginTop: 15 },
  primaryWideButtonText: { color: '#FFFFFF', fontSize: 13, fontWeight: '800' },
  emptyAccounts: { backgroundColor: '#FFFFFF', borderRadius: 17, padding: 18, marginBottom: 18 },
  emptyTitle: { color: '#202B40', fontSize: 15, fontWeight: '800', marginBottom: 5 },
  accountCard: { backgroundColor: '#FFFFFF', borderRadius: 16, padding: 15, borderWidth: 1, borderColor: '#EEF0F5', marginBottom: 10 },
  accountCardSelected: { borderColor: '#496BA8', borderWidth: 2 },
  accountTopRow: { flexDirection: 'row', alignItems: 'center' },
  accountIcon: { width: 40, height: 40, borderRadius: 13, backgroundColor: '#EEF3FC', alignItems: 'center', justifyContent: 'center' },
  accountIconText: { color: '#243B70', fontSize: 17, fontWeight: '800' },
  accountNameWrap: { flex: 1, marginLeft: 11 },
  accountName: { color: '#202B40', fontSize: 14, fontWeight: '800' },
  accountMeta: { color: '#9AA3B2', fontSize: 10, marginTop: 4 },
  accountBalance: { color: '#243B70', fontSize: 14, fontWeight: '800' },
  selectedLabel: { color: '#496BA8', fontSize: 9, fontWeight: '800', letterSpacing: 1, marginTop: 10 },
  currentBalance: { color: '#496BA8', fontSize: 12, fontWeight: '700', marginBottom: 5 },
  transactionButtons: { flexDirection: 'row', gap: 9, marginTop: 14 },
  transactionButton: { flex: 1, paddingVertical: 13, borderRadius: 12, alignItems: 'center' },
  addButton: { backgroundColor: '#E7F7EF' },
  minusButton: { backgroundColor: '#FCEDEE' },
  transactionButtonText: { color: '#243B70', fontSize: 11, fontWeight: '800' },
  historyCard: { backgroundColor: '#FFFFFF', borderRadius: 17, padding: 16, marginBottom: 20 },
  historyRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 12, borderTopWidth: 1, borderTopColor: '#F0F2F6' },
  historyCopy: { flex: 1, paddingRight: 8 },
  historyTitle: { color: '#273449', fontSize: 12, fontWeight: '700' },
  historyDate: { color: '#9AA3B2', fontSize: 9, marginTop: 4 },
  historyAmountWrap: { alignItems: 'flex-end' },
  historyAmount: { color: '#17804B', fontSize: 12, fontWeight: '800' },
  negativeAmount: { color: '#B54747' },
  historyBalance: { color: '#9AA3B2', fontSize: 9, marginTop: 4 },
  bottomNav: { height: 66, backgroundColor: '#FFFFFF', borderTopWidth: 1, borderTopColor: '#E9EDF3', flexDirection: 'row', justifyContent: 'space-around', alignItems: 'center', paddingBottom: 4 },
  navItem: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 5 },
  navIcon: { color: '#9AA3B2', fontSize: 21, fontWeight: '700' },
  navLabel: { color: '#9AA3B2', fontSize: 10, fontWeight: '700', marginTop: 2 },
  navActive: { color: '#243B70' },
  footer: { color: '#AAB1BE', fontSize: 9, fontWeight: '700', letterSpacing: 1, textAlign: 'center', marginTop: 25 },
});
