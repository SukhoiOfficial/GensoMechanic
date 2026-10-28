package dev.gensomechanic.mining.content.miner;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MiningNumbers {
    public static String format(double value) {
        BigDecimal number = BigDecimal.valueOf(value);
        BigDecimal rounded = number.setScale(4, RoundingMode.HALF_UP);
        return (rounded.signum() == 0 && number.signum() != 0 ? number : rounded).stripTrailingZeros().toPlainString();
    }
    private MiningNumbers() {}
}
