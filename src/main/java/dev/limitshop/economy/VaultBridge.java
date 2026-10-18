package dev.limitshop.economy;

public interface VaultBridge {

    EconomyService findExternal();

    boolean registerInternal(InternalEconomy internal);

    void unregisterInternal();

    boolean internalRegistered();

    String providerName();
}
