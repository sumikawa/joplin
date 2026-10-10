import * as React from 'react';
import { ComponentType } from 'react';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import NotesScreen from './screens/Notes/Notes';
import SearchScreen from './screens/SearchScreen';
import { Platform, View, StyleSheet } from 'react-native';
import { AppState, Route } from '../utils/types';
import { themeStyle } from './global-style';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import useKeyboardState from '../utils/hooks/useKeyboardState';
import usePrevious from '@joplin/lib/hooks/usePrevious';
import { Theme } from '@joplin/lib/themes/type';
import { useMemo } from 'react';
import KeyboardAvoidingView from './KeyboardAvoidingView';
import useDexMode, { minDexWindowWidth } from '../utils/useDexMode';

interface Props {
	route: Route;
	// eslint-disable-next-line @typescript-eslint/no-explicit-any -- Each screen has different props (themeId, dispatch, navigation, visible, ...); typing the union would force a refactor of every screen
	screens: Record<string, { screen: ComponentType<any> }>;
	dispatch: Dispatch;
	themeId: number;
}

const useStyles = (theme: Theme) => {
	return useMemo(() => {
		return StyleSheet.create({
			keyboardAvoidingView: { flex: 1, backgroundColor: theme.backgroundColor },
			mobileRoot: { flex: 1 },
			mobilePane: { flex: 1 },
			hiddenPane: { display: 'none' },
			dexRoot: { flex: 1, flexDirection: 'row', backgroundColor: theme.backgroundColor },
			dexNotes: { width: 300, borderRightWidth: StyleSheet.hairlineWidth, borderRightColor: theme.dividerColor },
			dexNotesCompact: { width: 220 },
			dexWorkspace: { flex: 1, flexDirection: 'row' },
			dexNotePane: { flex: 1, minWidth: 0 },
		});
	}, [theme]);
};


const AppNavComponent: React.FC<Props> = (props) => {
	const keyboardState = useKeyboardState();
	const safeAreaPadding = useSafeAreaInsets();
	const { isDexMode: dexMode, windowWidth } = useDexMode();
	const isDexLayout = Platform.OS === 'android' && dexMode && windowWidth >= minDexWindowWidth;

	if (!props.route) throw new Error('Route must not be null');

	// Note: certain screens are kept into memory, in particular Notes and Search
	// so that the scroll position is not lost when the user navigate away from them.

	const route = props.route;
	let Screen = null;
	let notesScreenVisible = false;
	let searchScreenVisible = false;

	if (route.routeName === 'Notes') {
		notesScreenVisible = true;
	} else if (route.routeName === 'Search') {
		searchScreenVisible = true;
	} else {
		Screen = props.screens[route.routeName].screen;
	}

	const previousRouteName = usePrevious(route.routeName, '');

	// Keep the search screen loaded if the user is viewing a note from that search screen
	// so that if the back button is pressed, the screen is still loaded. However, unload
	// it if navigating away.
	const searchScreenLoaded = searchScreenVisible || (previousRouteName === 'Search' && route.routeName === 'Note');

	const theme = themeStyle(props.themeId);
	const styles = useStyles(theme);
	// Workaround: On Android 15 and 16, the main app content seems to auto-resize when the keyboard is shown.
	// On earlier Android versions (and in modals), this does not seem to be the case.
	const keyboardAvoidingViewEnabled =
		(Platform.OS === 'android' && Platform.Version < 35)
		|| Platform.OS === 'ios';
	const autocompletionBarPadding = keyboardState.keyboardVisible && keyboardAvoidingViewEnabled ? safeAreaPadding.top : 0;
	const isNoteRoute = route.routeName === 'Note';
	const isSearchPane = route.routeName === 'Search' || (isNoteRoute && searchScreenLoaded);
	const listPaneVisible = notesScreenVisible || searchScreenVisible;

	// Keep the note screen at the same place in the React tree when moving between
	// phone and desktop displays, so pending edits and editor state survive the move.
	return (
		<KeyboardAvoidingView
			style={styles.keyboardAvoidingView}
			enabled={keyboardAvoidingViewEnabled}
		>
			<View style={isDexLayout ? styles.dexRoot : styles.mobileRoot}>
				<View style={isDexLayout ? [styles.dexNotes, windowWidth < 1100 && styles.dexNotesCompact] : listPaneVisible ? styles.mobilePane : styles.hiddenPane}>
					<NotesScreen visible={isDexLayout ? !isSearchPane : notesScreenVisible} dexLayout={isDexLayout} />
					{searchScreenLoaded && <SearchScreen visible={isDexLayout ? isSearchPane : searchScreenVisible} />}
				</View>
				<View style={isDexLayout ? styles.dexWorkspace : listPaneVisible ? styles.hiddenPane : styles.mobilePane}>
					{Screen && <Screen navigation={{ state: route }} themeId={props.themeId} dispatch={props.dispatch} {...(isNoteRoute ? { dexSplitPreview: isDexLayout } : {})} />}
					{isDexLayout && listPaneVisible && <View style={styles.dexNotePane} />}
				</View>
			</View>
			<View style={{ height: autocompletionBarPadding }} />
		</KeyboardAvoidingView>
	);
};

const AppNav = connect((state: AppState) => {
	return {
		route: state.route,
		themeId: state.settings.theme,
	};
})(AppNavComponent);

module.exports = { AppNav };
