package com.project.curve.kms.provider;

import com.project.curve.core.key.KeyProvider;
import com.project.curve.core.key.KeyProviderException;
import com.project.curve.kms.model.KeyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

@RequiredArgsConstructor
public class VaultKeyProvider implements KeyProvider {

    private final VaultTemplate vaultTemplate;
    private final String mountPath; // e.g., "secret" or "transit"

    @Override
    public String getDataKey(String keyId) {
        String path = String.format("%s/data/%s", mountPath, keyId);

        VaultResponseSupport<KeyResponse> response = vaultTemplate.read(path, KeyResponse.class);

        if (response != null && response.getData() != null) {
            return response.getData().key();
        }
        throw new KeyProviderException("Key not found in Vault: " + keyId);
    }
}
