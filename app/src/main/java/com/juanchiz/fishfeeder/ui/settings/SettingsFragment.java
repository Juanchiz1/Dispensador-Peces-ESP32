package com.juanchiz.fishfeeder.ui.settings;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.data.PrefsManager;
import com.juanchiz.fishfeeder.network.RetrofitClient;
import com.juanchiz.fishfeeder.ui.MainActivity;

/** Muestra la IP conectada, permite ajustar notificaciones y desconectar el dispensador. */
public class SettingsFragment extends Fragment {

    private PrefsManager prefsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefsManager = new PrefsManager(requireContext());

        TextView tvCurrentIp = view.findViewById(R.id.tvCurrentIp);
        MaterialButton btnDisconnect = view.findViewById(R.id.btnDisconnect);
        MaterialButton btnConnectDevice = view.findViewById(R.id.btnConnectDevice);

        boolean isConnected = prefsManager.isConnected();
        tvCurrentIp.setText(isConnected ? prefsManager.getDeviceIp() : getString(R.string.not_connected_yet));
        btnDisconnect.setVisibility(isConnected ? View.VISIBLE : View.GONE);
        btnConnectDevice.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        btnConnectDevice.setOnClickListener(v -> goToConnectScreen());

        SwitchMaterial switchNotifyEmpty = view.findViewById(R.id.switchNotifyEmpty);
        SwitchMaterial switchNotifyHumidity = view.findViewById(R.id.switchNotifyHumidity);
        switchNotifyEmpty.setChecked(prefsManager.isNotifyEmptyEnabled());
        switchNotifyHumidity.setChecked(prefsManager.isNotifyHumidityEnabled());

        switchNotifyEmpty.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefsManager.setNotifyEmptyEnabled(isChecked));
        switchNotifyHumidity.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefsManager.setNotifyHumidityEnabled(isChecked));

        btnDisconnect.setOnClickListener(v -> confirmDisconnect());
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.disconnect_confirm_title)
                .setMessage(R.string.disconnect_confirm_message)
                .setPositiveButton(R.string.accept, (dialog, which) -> disconnect())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void disconnect() {
        RetrofitClient.reset();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).restartToConnectScreen();
        }
    }

    /** Manda a la pantalla de Conectar sin borrar nada; a diferencia de disconnect(), esto es
     *  para cuando el usuario entró con "Entrar sin conectar" y ahora sí quiere ingresar la IP. */
    private void goToConnectScreen() {
        android.content.Intent intent = new android.content.Intent(requireContext(),
                com.juanchiz.fishfeeder.ui.connect.ConnectActivity.class);
        startActivity(intent);
    }
}
