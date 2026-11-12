package me.khajiitos.servercountryflags.common;

final class APITimeoutManager {
    private static int requestsLeft = 45;
    private static long cooldownReset = -1;
    private static int inFlight;

    static synchronized boolean tryStartRequest() {
        if (requestsLeft <= inFlight && System.currentTimeMillis() < cooldownReset) {
            return false;
        }
        inFlight++;
        return true;
    }

    static synchronized void finishRequest(int remaining, int secondsUntilReset) {
        inFlight--;
        if (remaining >= 0 && secondsUntilReset >= 0) {
            long now = System.currentTimeMillis();
            requestsLeft = now >= cooldownReset ? remaining : Math.min(requestsLeft, remaining);
            cooldownReset = now + secondsUntilReset * 1000L;
        }
    }
}
