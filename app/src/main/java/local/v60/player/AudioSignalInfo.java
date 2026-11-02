package local.v60.player;
final class AudioSignalInfo {
    static String frequency(long rate){return rate<=0?"sin datos":String.format(java.util.Locale.ROOT,"%.1f kHz",rate/1000.0);}
}
