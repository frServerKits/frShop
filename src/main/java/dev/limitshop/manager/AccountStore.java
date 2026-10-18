package dev.limitshop.manager;

import dev.limitshop.model.Account;
import dev.limitshop.storage.DataStore;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class AccountStore {

    private final DataStore dataStore;

    public AccountStore(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public Account getOrCreate(UUID uuid) {
        return dataStore.data().accounts.computeIfAbsent(uuid.toString(), key -> new Account());
    }

    public Account peek(UUID uuid) {
        return dataStore.data().accounts.get(uuid.toString());
    }

    public long balance(UUID uuid) {
        return getOrCreate(uuid).balance;
    }

    public void setBalance(UUID uuid, long amount) {
        Account account = getOrCreate(uuid);
        account.balance = Math.max(0L, amount);
        dataStore.markDirty();
    }

    public boolean withdraw(UUID uuid, long amount) {
        if (amount <= 0) {
            return true;
        }
        Account account = getOrCreate(uuid);
        if (account.balance < amount) {
            return false;
        }
        account.balance -= amount;
        dataStore.markDirty();
        return true;
    }

    public void deposit(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        Account account = getOrCreate(uuid);
        account.balance += amount;
        dataStore.markDirty();
    }

    /**
     * Adds debt. The first debt starts the grace timer and schedules the first
     * repossession attempt.
     */
    public void addDebt(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        Account account = getOrCreate(uuid);
        long now = System.currentTimeMillis();
        if (account.debt <= 0) {
            account.debtSince = now;
            account.lastCollect = now + TimeUnit.SECONDS.toMillis(
                    Math.max(0, dataStore.settings().debtGraceSeconds));
        }
        account.debt += amount;
        dataStore.markDirty();
    }

    public void setDebt(UUID uuid, long amount) {
        Account account = getOrCreate(uuid);
        long clean = Math.max(0L, amount);
        long now = System.currentTimeMillis();
        if (clean <= 0) {
            account.debt = 0;
            account.debtSince = 0;
            account.lastCollect = 0;
        } else {
            if (account.debt <= 0) {
                account.debtSince = now;
                account.lastCollect = now + TimeUnit.SECONDS.toMillis(
                        Math.max(0, dataStore.settings().debtGraceSeconds));
            }
            account.debt = clean;
        }
        dataStore.markDirty();
    }

    public void reduceDebt(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        Account account = getOrCreate(uuid);
        if (account.debt <= 0) {
            return;
        }
        account.debt = Math.max(0L, account.debt - amount);
        if (account.debt == 0) {
            account.debtSince = 0;
            account.lastCollect = 0;
        }
        dataStore.markDirty();
    }

    public void resetCollectionTimer(UUID uuid, long nextCollectAt) {
        Account account = getOrCreate(uuid);
        account.lastCollect = nextCollectAt;
        dataStore.markDirty();
    }

    public void markDirty() {
        dataStore.markDirty();
    }
}
