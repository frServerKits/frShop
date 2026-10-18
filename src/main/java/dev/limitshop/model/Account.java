package dev.limitshop.model;

public final class Account {

    public long balance = 0L;
    public long debt = 0L;

    /**
     * Epoch millis at which the debt was first created. 0 when the account has no debt.
     */
    public long debtSince = 0L;

    /**
     * Epoch millis of the next repossession attempt. 0 when nothing is scheduled.
     */
    public long lastCollect = 0L;

    /**
     * True after the first join, so the starting balance is only granted once.
     */
    public boolean initialized = false;
}
