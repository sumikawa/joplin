import { useEffect, useState } from 'react';
import { AppState, DeviceEventEmitter, Dimensions, NativeModules, Platform } from 'react-native';

interface DexModeNativeModule {
	getWindowState: ()=> Promise<DexWindowState>;
}

interface DexWindowState {
	isDexMode: boolean;
	windowWidth: number;
}

export const minDexWindowWidth = 850;

const useDexMode = () => {
	const [windowState, setWindowState] = useState<DexWindowState>({ isDexMode: false, windowWidth: 0 });

	useEffect(() => {
		const dexModeModule = NativeModules.DexModeModule as DexModeNativeModule|undefined;
		if (Platform.OS !== 'android' || !dexModeModule) return undefined;

		let active = true;
		let requestGeneration = 0;
		const updateDexMode = (value: DexWindowState) => {
			requestGeneration++;
			if (active) setWindowState(value);
		};
		const refreshDexMode = async () => {
			const generation = ++requestGeneration;
			try {
				const value = await dexModeModule.getWindowState();
				if (active && generation === requestGeneration) setWindowState(value);
			} catch {
				if (active && generation === requestGeneration) setWindowState({ isDexMode: false, windowWidth: 0 });
			}
		};

		void refreshDexMode();
		const subscription = DeviceEventEmitter.addListener('dexModeChanged', updateDexMode);
		const windowSubscription = Dimensions.addEventListener('change', refreshDexMode);
		const appStateSubscription = AppState.addEventListener('change', state => {
			if (state === 'active') void refreshDexMode();
		});

		return () => {
			active = false;
			subscription.remove();
			windowSubscription.remove();
			appStateSubscription.remove();
		};
	}, []);

	return windowState;
};

export default useDexMode;
