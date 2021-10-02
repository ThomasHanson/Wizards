package dev.thomashanson.wizards.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MathUtil {

    public static String formatTime(long time, int trim) {

        TimeUnit unit;

        unit =
                (time < 60 * 1000) ? TimeUnit.SECONDS :
                (time < 60 * 60 * 1000) ? TimeUnit.MINUTES :
                (time < 24 * 60 * 60 * 1000) ? TimeUnit.HOURS : TimeUnit.DAYS;

        String text = "";
        double num = 0;

        if (trim != 0) {

            text =
                    (unit == TimeUnit.MINUTES) ? (num = trim(trim, time / (60 * 1000))) + " minute" :
                            (unit == TimeUnit.SECONDS) ? (num = trim(trim, time) / 1000) + " second" :
                                    (int) (num = (int) trim(0, time)) + " millisecond";
        }

        if (num != 1)
            text += "s";

        return text;
    }

    public static double trim(int degree, double decimal) {

        DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat decimalFormat = new DecimalFormat("#.#" + "#".repeat(Math.max(0, degree - 1)), formatSymbols);

        return Double.parseDouble(decimalFormat.format(decimal));
    }

    public static Vector getTrajectory(Location from, Location to) {
        return getTrajectory(from.toVector(), to.toVector());
    }

    public static Vector getTrajectory(Vector from, Vector to)
    {
        return to.subtract(from).normalize();
    }

    public static Vector getTrajectory2D(Entity from, Entity to) {
        return getTrajectory2D(from.getLocation().toVector(), to.getLocation().toVector());
    }

    public static Vector getTrajectory2D(Location from, Location to) {
        return getTrajectory2D(from.toVector(), to.toVector());
    }

    public static Vector getTrajectory2D(Vector from, Vector to)
    {
        return to.subtract(from).setY(0).normalize();
    }

    public static double getOffset2D(Location a, Location b) {
        return getOffset2D(a.toVector(), b.toVector());
    }

    public static double getOffset2D(Vector a, Vector b) {
        a.setY(0);
        b.setY(0);
        return a.subtract(b).length();
    }

    public static Vector getPerpendicular(Vector onto, Vector u) {
        return u.clone().subtract(getProjection(onto, u));
    }

    public static Vector getProjection(Vector onto, Vector u) {
        return onto.clone().multiply(onto.dot(u) / onto.lengthSquared());
    }

    public static void setVelocity(Entity entity, double strength, double yAdd, double yMax, boolean groundBoost) {
        setVelocity(entity, entity.getLocation().getDirection(), strength, false, 0, yAdd, yMax, groundBoost);
    }

    public static void setVelocity(Entity entity, Vector vector, double strength, boolean ySet, double yBase, double yAdd, double yMax, boolean groundBoost) {

        if (Double.isNaN(vector.getX()) || Double.isNaN(vector.getY()) || Double.isNaN(vector.getZ()) || vector.length() == 0)
            return;

        if (ySet)
            vector.setY(yBase);

        vector.normalize();
        vector.multiply(strength);
        vector.setY(Math.min(vector.getY() + yAdd, yMax));

        if (groundBoost)
            if (entity.isOnGround())
                vector.setY(vector.getY() + 0.2);

        entity.setFallDistance(0F);
        entity.setVelocity(vector);
    }
}
