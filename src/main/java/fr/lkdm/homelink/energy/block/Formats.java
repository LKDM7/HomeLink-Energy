package fr.lkdm.homelink.energy.block;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number formatting for HE amounts and HE/t flows, with a stable grouping separator. */
public final class Formats {
    private Formats() { }

    /** @param amount energy amount
     *  @return "40,000" */
    public static String energy(long amount) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(amount);
    }

    /** @param rate energy flow per tick
     *  @return "2.62" or "0.261" for small flows */
    public static String rate(double rate) {
        String pattern = Math.abs(rate) >= 10 ? "#,##0.0" : Math.abs(rate) >= 1 ? "0.00" : "0.000";
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT)).format(rate);
    }
}
