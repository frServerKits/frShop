package dev.limitshop.economy;

import dev.limitshop.manager.AccountStore;

import java.util.UUID;

public final class InternalEconomy implements EconomyService {

    private final AccountStore accounts;

    public InternalEconomy(AccountStore accounts) {
        this.accounts = accounts;
    }

    @Override
    public long getBalance(UUID uuid) {
        return accounts.balance(uuid);
    }

    @Override
    public boolean withdraw(UUID uuid, long amount) {
        return accounts.withdraw(uuid, amount);
    }

    @Override
    public void deposit(UUID uuid, long amount) {
        accounts.deposit(uuid, amount);
    }

    @Override
    public void setBalance(UUID uuid, long amount) {
        accounts.setBalance(uuid, amount);
    }

    @Override
    public boolean internal() {
        return true;
    }

    @Override
    public String name() {
        return "LimitShop";
    }
}
