package net.microfalx.store.core;

import net.microfalx.lang.FormatterUtils;

public class StoreSettings {

    private long maximumMemorySize = 10 * FormatterUtils.M;

    public long getMaximumMemorySize() {
        return maximumMemorySize;
    }

    public void setMaximumMemorySize(long maximumMemorySize) {
        this.maximumMemorySize = maximumMemorySize;
    }
}
