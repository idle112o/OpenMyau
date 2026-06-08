package myau.clientanticheat;

public class CheckBuffer {
    private double value;

    public boolean flag(double add, double threshold) {
        this.value += add;
        return this.value >= threshold;
    }

    public void decay(double amount) {
        this.value = Math.max(0.0D, this.value - amount);
    }

    public void reset() {
        this.value = 0.0D;
    }

    public double get() {
        return this.value;
    }
}
