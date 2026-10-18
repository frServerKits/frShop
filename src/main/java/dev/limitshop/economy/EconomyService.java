package dev.limitshop.economy;

import java.util.UUID;

public interface EconomyService {

    long getBalance(UUID uuid);

    boolean withdraw(UUID uuid, long amount);

    void deposit(UUID uuid, long amount);

    void setBalance(UUID uuid, long amount);

    boolean internal();

    String name();
}
