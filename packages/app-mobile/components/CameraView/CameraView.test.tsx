import * as React from 'react';
import CameraView from './CameraView';
import { CameraResult } from './types';
import { render, screen, waitFor } from '../../utils/testing/testingLibrary';
import createMockReduxStore from '../../utils/testing/createMockReduxStore';
import TestProviderStack from '../testing/TestProviderStack';
import { acceptCameraPermission, rejectCameraPermission, startCamera, takePhoto } from './utils/testing';

interface WrapperProps {
	onPhoto?: (result: CameraResult)=> void;
	onInsertBarcode?: (text: string)=> void;
	onCancel?: ()=> void;
}

const emptyFn = ()=>{};
const store = createMockReduxStore();
const CameraViewWrapper: React.FC<WrapperProps> = props => {
	return <TestProviderStack store={store}>
		<CameraView
			style={{}}
			onPhoto={props.onPhoto ?? emptyFn}
			onInsertBarcode={props.onInsertBarcode ?? emptyFn}
			onCancel={props.onCancel ?? emptyFn}
		/>
	</TestProviderStack>;
};

describe('CameraView', () => {
	test('should hide permissions error if camera permission is granted', async () => {
		const view = render(<CameraViewWrapper/>);

		const queryPermissionsError = () => screen.queryByText('Missing camera permission');

		expect(queryPermissionsError()).toBeNull();
		rejectCameraPermission();
		expect(queryPermissionsError()).toBeVisible();
		acceptCameraPermission();
		expect(queryPermissionsError()).toBeNull();

		expect(await screen.findByRole('button', { name: 'Back' })).toBeVisible();
		startCamera();
		expect(await screen.findByRole('button', { name: 'Take photo' })).toBeVisible();

		view.unmount();
	});

	test('should retain photo capture without barcode scanning controls', async () => {
		const onPhoto = jest.fn();
		const view = render(<CameraViewWrapper onPhoto={onPhoto}/>);
		acceptCameraPermission();
		startCamera();

		expect(screen.queryByRole('button', { name: 'QR Code' })).toBeNull();
		await takePhoto();
		await waitFor(() => expect(onPhoto).toHaveBeenCalledTimes(1));
		expect(onPhoto).toHaveBeenCalledWith(expect.objectContaining({ type: 'image/svg+xml' }));

		view.unmount();
	});
});

