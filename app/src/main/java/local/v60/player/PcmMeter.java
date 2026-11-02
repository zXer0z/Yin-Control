package local.v60.player;

/** Peak/RMS of actual PCM, in 100 ms bins indexed by decoded audio time. */
final class PcmMeter {
    static final int SIZE=128;
    final int rate,channels,encoding,width,frameBytes;
    private final long[] ids=new long[SIZE],counts=new long[SIZE];
    private final double[] energy=new double[SIZE],peak=new double[SIZE];
    private final byte[] carry;
    private int carried;private long frames;
    PcmMeter(int rate,int channels,int encoding){this.rate=rate;this.channels=channels;this.encoding=encoding;width=encoding==3?1:encoding==2?2:encoding==21?3:4;frameBytes=width*channels;carry=new byte[frameBytes];java.util.Arrays.fill(ids,-1);}
    synchronized void accept(byte[] bytes,int offset,int length){int end=offset+length;if(carried>0){int n=Math.min(frameBytes-carried,length);System.arraycopy(bytes,offset,carry,carried,n);carried+=n;offset+=n;if(carried==frameBytes){frame(carry,0);carried=0;}}
        for(;offset+frameBytes<=end;offset+=frameBytes)frame(bytes,offset);if(offset<end){carried=end-offset;System.arraycopy(bytes,offset,carry,0,carried);}}
    private void frame(byte[] b,int at){long id=frames*10/rate;int index=(int)(id%SIZE);if(ids[index]!=id){ids[index]=id;counts[index]=0;energy[index]=0;peak[index]=0;}for(int ch=0;ch<channels;ch++){double x=sample(b,at+ch*width);if(!Double.isFinite(x))x=0;energy[index]+=x*x;counts[index]++;peak[index]=Math.max(peak[index],Math.abs(x));}frames++;}
    private double sample(byte[] b,int i){if(encoding==3)return ((b[i]&255)-128)/128.0;int v=(b[i]&255)|((b[i+1]&255)<<8);if(width==2)return (short)v/32768.0;v|=(b[i+2]&255)<<16;if(width==3)return ((v<<8)>>8)/8388608.0;v|=b[i+3]<<24;return encoding==4?Float.intBitsToFloat(v):v/2147483648.0;}
    synchronized long lastComplete(){return frames*10/rate-1;}
    synchronized double[] level(long first,long last){double sum=0,p=0;long n=0;for(int i=0;i<SIZE;i++)if(ids[i]>=first&&ids[i]<=last){sum+=energy[i];n+=counts[i];p=Math.max(p,peak[i]);}return new double[]{n==0?0:sum/n,p,n};}
    double[] recent(){long last=lastComplete();return level(Math.max(0,last-3),last);}
    static double db(double amplitude){return 20*Math.log10(Math.max(1e-6,amplitude));}
}
