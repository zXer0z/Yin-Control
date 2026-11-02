/* Verified Android16 / stock13 arm64 AudioTrack::set ABI. No object layouts or
 * stack arguments change. Only the requesting thread's flags (w6), and for
 * stock high-rate PCM / DSD128 its sample rate (w2), are adjusted.
 * LSPosed owns the hook. PCM never requests the DOP flag. */
typedef unsigned int u32;
typedef unsigned long u64;
typedef unsigned short u16;
typedef long s64;
typedef struct {u32 type,flags;u64 offset,vaddr,paddr,filesz,memsz,align;} Phdr;
typedef struct {u64 addr;const char *name;const Phdr *phdr;u16 phnum;} PhdrInfo;
typedef struct {s64 tag;u64 value;} Dyn;
typedef struct {u32 name;unsigned char info,other;u16 shndx;u64 value,size;} Sym;
extern int dl_iterate_phdr(int (*callback)(PhdrInfo*,u64,void*),void*);
extern int __android_log_print(int,const char*,const char*,...);
#define EXPORT __attribute__((visibility("default"),used))
__attribute__((visibility("hidden"))) void *v60_original;
__attribute__((visibility("hidden"))) u64 v60_request_thread;
__attribute__((visibility("hidden"))) u32 v60_request_flags;
__attribute__((visibility("hidden"))) u32 v60_request_rate;
static int installed;
static const char symbol[]="_ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi";
static int equal(const char *a,const char *b){while(*a&&*a==*b){a++;b++;}return *a==*b;}
static int contains(const char *a,const char *b){for(;*a;a++){const char*x=a,*y=b;while(*y&&*x==*y){x++;y++;}if(!*y)return 1;}return 0;}
static u64 thread_pointer(void){u64 value;__asm__("mrs %0, tpidr_el0":"=r"(value));return value;}
/* x16/x17 are AArch64 scratch registers. The tail branch preserves arguments
 * except requested w6/w2, plus lr and stack arguments. No stack frame/calls. */
__attribute__((naked)) static void replacement(void){__asm__(
    "adrp x16, v60_request_thread\n"
    "ldr x17, [x16, :lo12:v60_request_thread]\n"
    "mrs x16, tpidr_el0\n"
    "cmp x16, x17\n"
    "b.ne 1f\n"
    "adrp x16, v60_request_flags\n"
    "ldr w17, [x16, :lo12:v60_request_flags]\n"
    "orr w6, w6, w17\n"
    "adrp x16, v60_request_rate\n"
    "ldr w17, [x16, :lo12:v60_request_rate]\n"
    "cbz w17, 1f\n"
    "mov w2, w17\n"
    "1: adrp x16, v60_original\n"
    "ldr x16, [x16, :lo12:v60_original]\n"
    "br x16\n");}
typedef int (*Hook)(void*,void*,void**);
typedef struct {u32 version;Hook hook;int (*unhook)(void*);} NativeAPIEntries;
static Hook hook;
static int locate(PhdrInfo *info,u64 size,void *data){
    (void)size;(void)data;
    if(!info->name||!contains(info->name,"/libaudioclient.so"))return 0;
    Dyn *dynamic=0;
    for(u16 i=0;i<info->phnum;i++)if(info->phdr[i].type==2)dynamic=(Dyn*)(info->addr+info->phdr[i].vaddr);
    if(!dynamic)return 0;
    Sym *symbols=0;const char *strings=0;u32 *hash=0;u32 *gnu=0;
    for(Dyn*d=dynamic;d->tag;d++){
        /* Bionic leaves DT pointers relative to the ELF load bias. */
        u64 address=info->addr+d->value;
        if(d->tag==6)symbols=(Sym*)address;
        if(d->tag==5)strings=(const char*)address;
        if(d->tag==4)hash=(u32*)address;
        if(d->tag==0x6ffffef5)gnu=(u32*)address;
    }
    if(!symbols||!strings)return 0;
    u32 count=hash?hash[1]:0;
    if(!count&&gnu){
        u32 buckets=gnu[0],first=gnu[1],bloom=gnu[2];
        u32 *bucket=(u32*)((u64*)(gnu+4)+bloom),*chain=bucket+buckets;
        u32 last=0;for(u32 i=0;i<buckets;i++)if(bucket[i]>last)last=bucket[i];
        if(last>=first){while(!(chain[last-first]&1))last++;count=last+1;}
    }
    if(!count||count>1000000)return 0;
    for(u32 i=0;i<count;i++)if(symbols[i].shndx&&equal(strings+symbols[i].name,symbol)){
        int result=hook((void*)(info->addr+symbols[i].value),(void*)replacement,&v60_original);
        installed=result==0&&v60_original!=0;
        __android_log_print(4,"V60Direct","AudioTrack set hook: %d",installed);
        return 1;
    }
    return 0;
}
static void loaded(const char *name,void *handle){(void)handle;if(!installed&&contains(name,"libaudioclient.so"))dl_iterate_phdr(locate,0);}
EXPORT void (*native_init(const NativeAPIEntries *api))(const char*,void*){
    if(api&&api->version>=1&&api->hook){hook=api->hook;dl_iterate_phdr(locate,0);}
    return loaded;
}
EXPORT int Java_local_v60_player_DirectModule_nativeArm(void *env,void *clazz,int enabled){
    (void)env;(void)clazz;
    if(!installed)return 0;
    v60_request_flags=1;v60_request_rate=0;
    v60_request_thread=enabled?thread_pointer():0;
    return 1;
}
EXPORT int Java_local_v60_player_DirectModule_nativeReady(void *env,void *clazz){(void)env;(void)clazz;return installed;}
EXPORT int Java_local_v60_player_DirectModule_nativeArmPcmRate(void *env,void *clazz,int rate){
    (void)env;(void)clazz;if(!installed||(rate!=352800&&rate!=384000))return 0;
    /* PCM only: never sets the LG DOP flag. Verified stock13 ABI in Java. */
    v60_request_flags=1;v60_request_rate=(u32)rate;v60_request_thread=thread_pointer();return 1;
}
EXPORT int Java_local_v60_player_DirectModule_nativeArmDop(void *env,void *clazz){
    (void)env;(void)clazz;if(!installed)return 0;
    /* Flag bit 23 is tested by the installed LG HAL's DoP path. */
    v60_request_flags=0x800001;v60_request_rate=0;v60_request_thread=thread_pointer();return 1;
}
EXPORT int Java_local_v60_player_DirectModule_nativeArmDop128(void *env,void *clazz){
    (void)env;(void)clazz;if(!installed)return 0;
    /* Exact stock13 only at the Java bridge. w2 is AudioTrack::set sampleRate. */
    v60_request_flags=0x800001;v60_request_rate=352800;v60_request_thread=thread_pointer();return 1;
}
typedef struct {const char *name;void *address;} SymbolRequest;
EXPORT int Java_local_v60_player_DirectModule_nativeArmProbe(void *env,void *clazz,int flags){
    (void)env;(void)clazz;if(!installed||(flags!=1&&flags!=0x100001))return 0;
    v60_request_flags=(u32)flags;v60_request_rate=0;v60_request_thread=thread_pointer();return 1;
}
static int find_conversion(PhdrInfo *info,u64 size,void *data){
    (void)size;
    if(!info->name||!contains(info->name,"/libaudio_aidl_conversion_common_cpp.so"))return 0;
    SymbolRequest *request=(SymbolRequest*)data;Dyn *dynamic=0;
    for(u16 i=0;i<info->phnum;i++)if(info->phdr[i].type==2)dynamic=(Dyn*)(info->addr+info->phdr[i].vaddr);
    if(!dynamic)return 0;
    Sym *symbols=0;const char *strings=0;u32 *hash=0,*gnu=0;
    for(Dyn *d=dynamic;d->tag;d++){
        u64 address=info->addr+d->value;
        if(d->tag==6)symbols=(Sym*)address;if(d->tag==5)strings=(const char*)address;
        if(d->tag==4)hash=(u32*)address;if(d->tag==0x6ffffef5)gnu=(u32*)address;
    }
    if(!symbols||!strings)return 0;u32 count=hash?hash[1]:0;
    if(!count&&gnu){u32 first=gnu[1],*bucket=(u32*)((u64*)(gnu+4)+gnu[2]),*chain=bucket+gnu[0],last=0;
        for(u32 i=0;i<gnu[0];i++)if(bucket[i]>last)last=bucket[i];
        if(last>=first){while(!(chain[last-first]&1))last++;count=last+1;}
    }
    if(count>1000000)return 0;
    for(u32 i=0;i<count;i++)if(symbols[i].shndx&&equal(strings+symbols[i].name,request->name)){request->address=(void*)(info->addr+symbols[i].value);return 1;}
    return 0;
}
EXPORT u64 Java_local_v60_player_DirectModule_nativeConvertFlags(void *env,void *clazz,int flags,int reverse){
    (void)env;(void)clazz;
    SymbolRequest request={reverse?"_ZN7android45aidl2legacy_int32_t_audio_output_flags_t_maskEi":"_ZN7android45legacy2aidl_audio_output_flags_t_int32_t_maskE20audio_output_flags_t",0};
    dl_iterate_phdr(find_conversion,&request);if(!request.address)return ~(u64)0;
    /* Installed arm64 Android16 binary returns the trivial 8-byte expected<int,int>
     * in x0: low32 value/error; high32 has_error. No C++ object is allocated. */
    return ((u64(*)(u32))request.address)((u32)flags);
}
