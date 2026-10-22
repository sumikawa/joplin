import { CameraDirection } from '@joplin/lib/models/settings/builtInMetadata';
import { ViewStyle } from 'react-native';
import { CameraResult } from '../types';

export interface Props {
	style: ViewStyle;
	cameraType: CameraDirection;
	ratio: string|undefined;

	onCameraReady: ()=> void;
	onPermissionRequestFailure: ()=> void;
	onHasPermission: ()=> void;
}

export interface CameraRef {
	takePictureAsync(): Promise<CameraResult>;
}
