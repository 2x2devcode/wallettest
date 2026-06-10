# 2x2 Wallet — Aplicativo Android Nativo

**Versão do aplicativo: 1.6.5**

Carteira cripto nativa para a rede **2x2Coin**, baseada no projeto [2x2Coin](https://github.com/coinsdevcode/2x2Coin). O aplicativo conecta-se diretamente à rede P2P da moeda, baixa cabeçalhos e blocos da blockchain, e oferece gestão completa de saldo, depósitos, transferências e saques.

## Funcionalidades

| Tela | Descrição |
|------|-----------|
| **Saldo** | Exibe saldo disponível, status de sincronização da blockchain e transações recentes |
| **Depósitos** | Mostra endereços de recebimento (CashAddr e Base58) com QR Code |
| **Transferências** | Envia 2X2 para outro endereço na rede |
| **Saques** | Retira fundos para endereço externo |

## Arquitetura

```
app/
├── chain/          # Parâmetros da rede, blocos, transações
├── crypto/         # Base58, CashAddr, secp256k1, scrypt, SHA256
├── network/        # Cliente P2P (porta 15190)
├── sync/           # Serviço de sincronização da blockchain
├── wallet/         # Gestão de chaves e construção de transações
├── data/db/        # Room (cabeçalhos, UTXOs, transações)
└── ui/             # Jetpack Compose (Material 3)
```

### Sincronização da blockchain

1. Descobre peers via DNS seeds da rede 2x2Coin (`seed.quimeralabs.org`, etc.)
2. Handshake P2P (protocolo v70026)
3. Baixa cabeçalhos de blocos via `getheaders` / `headers`
4. Baixa blocos completos via `getdata` / `block`
5. Indexa UTXOs que pertencem à carteira local
6. Executa em foreground service com notificação de progresso

### Parâmetros da rede (mainnet)

| Parâmetro | Valor |
|-----------|-------|
| P2P Port | 15190 |
| RPC Port | 15189 |
| Magic bytes | `0x32 0x78 0x32 0x43` ("2x2C") |
| Endereço Base58 | prefixo `3` |
| CashAddr | `2x2coin:` |
| Consenso | PoW (até bloco 110000) + PoS |
| Hash PoW | scrypt-1024-1-1-256 |
| Hash PoS | double SHA-256 |

## Requisitos

- Android 8.0+ (API 26)
- Android Studio Ladybug ou superior
- JDK 17

## Build

### Opção 1 — Android Studio (recomendado)

Abra o projeto no Android Studio. O SDK é detectado automaticamente em `~/Android/Sdk`.

### Opção 2 — Linha de comando

```bash
git clone https://github.com/2x2devcode/wallettest.git
cd wallettest

# Se já tiver o SDK (ex.: Android Studio):
export ANDROID_HOME=$HOME/Android/Sdk   # Linux
# export ANDROID_HOME=$HOME/Library/Android/sdk  # macOS

# Ou instale o SDK automaticamente (Linux):
chmod +x scripts/setup-android-sdk.sh
./scripts/setup-android-sdk.sh

# Build debug
./gradlew assembleDebug
```

O Gradle tenta localizar o SDK automaticamente via `ANDROID_HOME`, `ANDROID_SDK_ROOT` ou caminhos comuns. Se `local.properties` estiver vazio, ele será recriado na primeira build.

Copie `local.properties.example` para `local.properties` apenas se precisar definir o caminho manualmente.

O APK será gerado em `app/build/outputs/apk/debug/app-debug-1.6.5.apk`.

## Segurança

- Chaves privadas armazenadas com `EncryptedSharedPreferences` (AES-256-GCM)
- Carteira criada automaticamente no primeiro uso
- Transações assinadas localmente com secp256k1
- Broadcast via rede P2P (sem servidor central)

## Referências

- [2x2Coin GitHub](https://github.com/coinsdevcode/2x2Coin)
- [Site oficial](https://2x2coin.com/)
- [Block Explorer](https://explorer.2x2coin.com/)

## Licença

MIT — ver [LICENSE](LICENSE)
