package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionMutation;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionOperation;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import com.mojang.blaze3d.audio.OggAudioStream;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AudioCorruptionManager {
    private static final long AUDIO_MUTATION_SEED = 0x5350303450434D21L;
    private static final float MAX_SAFE_GAIN = 0.70794576F;
    private static final int MAX_SAFE_SAMPLE = 23197;
    private static Field soundManagerSoundEngineField;
    private static Field soundEngineSoundBuffersField;
    private static Field soundBufferLibraryResourceManagerField;
    private static long lastPcmReportMs;

    private AudioCorruptionManager() {
    }

    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        installMutatingSoundBuffers(event.getEngine());
    }

    public static void onSettingsChanged(CorruptionProfileSnapshot previous, CorruptionProfileSnapshot current) {
        if (previous == null || current == null
                || previous.getCorruptionLevel() != current.getCorruptionLevel()
                || previous.getFixedCorruptionSeed() != current.getFixedCorruptionSeed()
                || previous.getEnabledTargetsMask() != current.getEnabledTargetsMask()) {
            SoundManager soundManager = Minecraft.getInstance().getSoundManager();
            SoundEngine engine = soundEngine(soundManager);
            installMutatingSoundBuffers(engine);
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (sound == null || sound instanceof MutatedSoundInstance || !stack.active(CorruptionSurface.SOUND_STREAM)) {
            return;
        }

        String targetId = soundTargetId(sound);
        float intensity = audioIntensity(stack, targetId);
        if (intensity <= 0.0F) {
            return;
        }

        event.setSound(wrap(sound));
    }

    private static void installMutatingSoundBuffers(SoundEngine engine) {
        if (engine == null) {
            return;
        }

        Field soundBuffersField = soundEngineSoundBuffersField;
        if (soundBuffersField == null) {
            soundBuffersField = findField(SoundEngine.class, "soundBuffers", "f_120222_");
            soundEngineSoundBuffersField = soundBuffersField;
        }
        if (soundBuffersField == null) {
            return;
        }

        try {
            Object value = soundBuffersField.get(engine);
            if (!(value instanceof SoundBufferLibrary original) || value instanceof MutatingSoundBufferLibrary) {
                return;
            }
            ResourceProvider resources = resourceProvider(original);
            if (resources == null) {
                return;
            }
            soundBuffersField.set(engine, new MutatingSoundBufferLibrary(resources));
        } catch (IllegalAccessException ignored) {
        }
    }

    private static ResourceProvider resourceProvider(SoundBufferLibrary library) {
        Field field = soundBufferLibraryResourceManagerField;
        if (field == null) {
            field = findField(SoundBufferLibrary.class, "resourceManager", "f_120189_");
            soundBufferLibraryResourceManagerField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(library);
            return value instanceof ResourceProvider provider ? provider : null;
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    private static SoundEngine soundEngine(SoundManager soundManager) {
        if (soundManager == null) {
            return null;
        }
        Field field = soundManagerSoundEngineField;
        if (field == null) {
            field = findField(SoundManager.class, "soundEngine", "f_120349_");
            soundManagerSoundEngineField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(soundManager);
            return value instanceof SoundEngine engine ? engine : null;
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    private static AudioStream mutateStreamIfNeeded(AudioStream stream, String targetId, CorruptionEffectStack stack) {
        if (stream == null || stream instanceof MutatedPcmAudioStream || !shouldCorruptAudio(stack, targetId)) {
            return stream;
        }
        return new MutatedPcmAudioStream(stream, targetId, stack);
    }

    private static ByteBuffer mutateDecodedPcm(ByteBuffer source, AudioFormat format, String targetId, CorruptionEffectStack stack, long firstFrame) {
        ByteBuffer input = source == null ? BufferUtils.createByteBuffer(0) : source.slice();
        int byteCount = input.remaining();
        byte[] original = new byte[byteCount];
        input.get(original);
        ByteBuffer output = BufferUtils.createByteBuffer(byteCount);
        output.put(original);
        output.flip();

        if (byteCount <= 0 || format == null || !shouldCorruptAudio(stack, targetId) || !isSupportedPcm(format)) {
            return output;
        }

        int channels = Math.max(1, format.getChannels());
        int frameSize = format.getFrameSize() > 0 ? format.getFrameSize() : channels * 2;
        if (frameSize < channels * 2) {
            return output;
        }

        int frameCount = byteCount / frameSize;
        if (frameCount <= 0) {
            return output;
        }

        AudioMutationPlan plan = AudioMutationPlan.create(stack, targetId, format, firstFrame);
        int[] heldSamples = new int[channels];
        for (int channel = 0; channel < channels; channel++) {
            heldSamples[channel] = readSample(original, channel * 2);
        }

        for (int frame = 0; frame < frameCount; frame++) {
            long absoluteFrame = firstFrame + frame;
            long blockHash = mix(plan.seed() ^ (absoluteFrame / plan.blockFrames()) * 0xD1B54A32D192ED03L);
            boolean repeatSpan = unit(blockHash >>> 7) < plan.repeatChance();
            boolean byteSwapSpan = unit(blockHash >>> 17) < plan.byteSwapChance();
            boolean foldSpan = unit(blockHash >>> 27) < plan.foldChance();
            boolean silenceSpan = unit(blockHash >>> 37) < plan.silenceChance();
            boolean squareSpan = plan.squareMix() > 0.0F && unit(blockHash >>> 47) < plan.squareChance();
            int blockStart = (frame / plan.blockFrames()) * plan.blockFrames();
            int blockLimit = Math.min(frameCount, blockStart + plan.blockFrames());
            int repeatedFrame = blockStart + Math.floorMod((int) (blockHash >>> 11), Math.max(1, blockLimit - blockStart));

            for (int channel = 0; channel < channels; channel++) {
                int channelOffset = channel * 2;
                int sourceFrame = repeatSpan ? repeatedFrame : frame;
                if (plan.channelDesyncFrames() > 0 && channel > 0) {
                    sourceFrame = Math.floorMod(sourceFrame + plan.channelDesyncFrames() * channel, frameCount);
                }

                int sourceOffset = sourceFrame * frameSize + channelOffset;
                int targetOffset = frame * frameSize + channelOffset;
                int sample = readSample(original, sourceOffset);

                if (byteSwapSpan) {
                    sample = byteSwapSample(sample);
                }
                if (foldSpan) {
                    sample = foldSample(sample, plan.foldThreshold());
                }
                sample = bitCrush(sample, plan.crushBits());

                if (silenceSpan) {
                    sample = Math.abs(sample) < plan.foldThreshold() ? 0 : Integer.signum(sample) * (plan.foldThreshold() / 4);
                }

                int square = generatedWaveSample(absoluteFrame, channel, sample, blockHash, plan);
                float squareMix = squareSpan ? Math.min(0.98F, plan.squareMix() + 0.18F) : plan.squareMix() * 0.72F;
                sample = blendSample(sample, square, squareMix);

                if (plan.holdFrames() > 1) {
                    int holdPhase = Math.floorMod((int) (absoluteFrame + channel * 13L + plan.seed()), plan.holdFrames());
                    if (holdPhase == 0) {
                        heldSamples[channel] = sample;
                    } else if (unit(blockHash >>> (channel + 3)) < plan.holdChance()) {
                        sample = heldSamples[channel];
                    }
                }

                writeSample(output, targetOffset, clampSample(sample));
            }
        }

        reportPcmMutation();
        return output;
    }

    private static boolean isSupportedPcm(AudioFormat format) {
        return format.getSampleSizeInBits() == 16 && !format.isBigEndian() && format.getChannels() > 0;
    }

    private static boolean shouldCorruptAudio(CorruptionEffectStack stack, String targetId) {
        if (!stack.active(CorruptionSurface.SOUND_STREAM)) {
            return false;
        }
        float intensity = audioIntensity(stack, targetId);
        return intensity > 0.015F;
    }

    private static float audioIntensity(CorruptionEffectStack stack, String targetId) {
        float global = stack.intensity(CorruptionSurface.SOUND_STREAM);
        float target = stack.targetIntensity(CorruptionSurface.SOUND_STREAM, targetId);
        float highLevelPressure = smoothstep((stack.level() - 82.0F) / 18.0F);
        float floor = global * (0.28F + highLevelPressure * 0.22F) + stack.instability() * 0.035F;
        return clamp01(Math.max(target * 0.42F, floor));
    }

    private static int readSample(byte[] data, int offset) {
        if (offset < 0 || offset + 1 >= data.length) {
            return 0;
        }
        return (short) (Byte.toUnsignedInt(data[offset]) | (data[offset + 1] << 8));
    }

    private static void writeSample(ByteBuffer output, int offset, int sample) {
        if (offset < 0 || offset + 1 >= output.limit()) {
            return;
        }
        int clamped = clampSample(sample);
        output.put(offset, (byte) (clamped & 0xFF));
        output.put(offset + 1, (byte) ((clamped >>> 8) & 0xFF));
    }

    private static int byteSwapSample(int sample) {
        int unsigned = sample & 0xFFFF;
        return (short) (((unsigned & 0xFF) << 8) | ((unsigned >>> 8) & 0xFF));
    }

    private static int bitCrush(int sample, int clearBits) {
        int bits = Math.max(0, Math.min(14, clearBits));
        return sample >> bits << bits;
    }

    private static int foldSample(int sample, int threshold) {
        int limit = Math.max(512, Math.min(32000, threshold));
        int sign = sample < 0 ? -1 : 1;
        int value = Math.abs(sample);
        if (value <= limit) {
            return sample;
        }
        int period = limit * 2;
        int folded = Math.floorMod(value - limit, period);
        int reflected = folded > limit ? folded - limit : limit - folded;
        return sign * reflected;
    }

    private static int generatedWaveSample(long frame, int channel, int sourceSample, long blockHash, AudioMutationPlan plan) {
        int period = Math.max(2, plan.squarePeriodFrames());
        int phase = Math.floorMod((int) (frame + channel * 17L + (plan.seed() >>> 13)), period);
        float normalized = phase / (float) period;
        int amplitude = Math.max(Math.abs(sourceSample), plan.squareAmplitude());
        return switch (plan.waveform()) {
            case 1 -> normalized < 0.28F + unit(blockHash >>> 29) * 0.44F ? amplitude : -amplitude;
            case 2 -> Math.round((normalized * 2.0F - 1.0F) * amplitude);
            case 3 -> Math.round((1.0F - Math.abs(normalized * 4.0F - 2.0F)) * amplitude);
            case 4 -> unit(blockHash ^ (frame / Math.max(1, period / 2)) * 0x9E3779B97F4A7C15L ^ channel * 0x632BE59BD9B4E019L) < 0.5F ? amplitude : -amplitude;
            default -> phase < period / 2 ? amplitude : -amplitude;
        };
    }

    private static int blendSample(int source, int target, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        return Math.round(source + (target - source) * clamped);
    }

    private static int clampSample(int sample) {
        return Math.max(-MAX_SAFE_SAMPLE, Math.min(MAX_SAFE_SAMPLE, sample));
    }

    private static void reportPcmMutation() {
        long now = System.currentTimeMillis();
        if (now - lastPcmReportMs > 1500L) {
            lastPcmReportMs = now;
        }
    }

    private static SoundInstance wrap(SoundInstance sound) {
        if (sound instanceof TickableSoundInstance tickable) {
            return new MutatedTickableSoundInstance(tickable);
        }
        return new MutatedSoundInstance(sound);
    }

    private static String soundTargetId(SoundInstance sound) {
        return sound.getSource().getName() + ":" + sound.getLocation();
    }

    private static long soundClock() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? System.currentTimeMillis() / 50L : minecraft.level.getGameTime();
    }

    private static String soundFileTargetId(ResourceLocation path) {
        return "pcm:" + path;
    }

    private static String audioSignature(CorruptionEffectStack stack, String targetId) {
        return stack.level()
                + ":" + stack.previousLevel()
                + ":" + stack.delta()
                + ":" + stack.stabilityDebt()
                + ":" + stack.profileCoherence()
                + ":" + Math.round(audioIntensity(stack, targetId) * 1000.0F)
                + ":" + stack.bucket(CorruptionSurface.SOUND_STREAM, targetId, 0x415544, 96);
    }

    private static Field findField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static float unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private record AudioMutationPlan(
            long seed,
            float repeatChance,
            float byteSwapChance,
            float foldChance,
            float silenceChance,
            float squareChance,
            float squareMix,
            float holdChance,
            int blockFrames,
            int holdFrames,
            int channelDesyncFrames,
            int crushBits,
            int foldThreshold,
            int squarePeriodFrames,
            int squareAmplitude,
            int waveform
    ) {
        static AudioMutationPlan create(CorruptionEffectStack stack, String targetId, AudioFormat format, long firstFrame) {
            float intensity = audioIntensity(stack, targetId);
            long seed = stack.stableLong(CorruptionSurface.SOUND_STREAM, targetId, 0x50434D)
                    ^ AUDIO_MUTATION_SEED
                    ^ firstFrame * 0x9E3779B97F4A7C15L;
            float instability = stack.instability();
            float sampleRate = Math.max(8000.0F, format.getSampleRate());
            int blockFrames = Math.max(24, Math.round(220.0F - intensity * 150.0F + unit(seed >>> 9) * 64.0F));
            int holdFrames = Math.max(3, Math.round(18.0F - intensity * 10.0F + unit(seed >>> 19) * 8.0F));
            int channelDesync = format.getChannels() <= 1 ? 0 : Math.round(intensity * (1.0F + unit(seed >>> 23) * 7.0F));
            int crushBits = Math.min(9, Math.round(intensity * 7.0F + instability * 2.0F));
            int foldThreshold = Math.max(6000, Math.round(31000.0F - intensity * 18000.0F - instability * 2200.0F));
            int frequency = Math.round(45.0F + unit(seed >>> 31) * 260.0F + intensity * 360.0F);
            int squarePeriod = Math.max(2, Math.round(sampleRate / Math.max(1.0F, frequency)));
            int squareAmplitude = Math.max(700, Math.min(MAX_SAFE_SAMPLE, Math.round(1200.0F + intensity * 12000.0F + unit(seed >>> 39) * 2400.0F)));
            float collapse = smoothstep((intensity - 0.60F) / 0.40F);
            int waveform = Math.floorMod((int) (seed >>> 52), 5);
            return new AudioMutationPlan(
                    seed,
                    Math.min(0.42F, 0.03F + intensity * 0.24F + instability * 0.05F),
                    Math.min(0.20F, 0.01F + intensity * 0.10F + instability * 0.03F),
                    Math.min(0.36F, 0.02F + intensity * 0.22F + instability * 0.04F),
                    Math.min(0.08F, 0.006F + intensity * 0.045F + instability * 0.025F),
                    Math.min(0.24F, Math.max(0.0F, intensity - 0.52F) * 0.45F + instability * 0.035F),
                    Math.min(0.48F, collapse * (0.22F + unit(seed >>> 45) * 0.25F)),
                    Math.min(0.42F, 0.03F + intensity * 0.22F + instability * 0.04F),
                    blockFrames,
                    holdFrames,
                    channelDesync,
                    crushBits,
                    foldThreshold,
                    squarePeriod,
                    squareAmplitude,
                    waveform
            );
        }
    }

    private static float smoothstep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static final class MutatingSoundBufferLibrary extends SoundBufferLibrary {
        private final ResourceProvider resources;
        private final Map<AudioCacheKey, CompletableFuture<SoundBuffer>> corruptedCache = new ConcurrentHashMap<>();

        private MutatingSoundBufferLibrary(ResourceProvider resources) {
            super(resources);
            this.resources = resources;
        }

        @Override
        public CompletableFuture<SoundBuffer> getCompleteBuffer(ResourceLocation path) {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundFileTargetId(path);
            AudioCacheKey key = new AudioCacheKey(path, audioSignature(stack, targetId));
            return corruptedCache.computeIfAbsent(key, location -> CompletableFuture.supplyAsync(() -> {
                try (
                        InputStream input = resources.open(location.path());
                        OggAudioStream stream = new OggAudioStream(input)
                ) {
                    ByteBuffer decoded = stream.readAll();
                    AudioFormat format = stream.getFormat();
                    ByteBuffer mutated = mutateDecodedPcm(decoded, format, targetId, stack, 0L);
                    return new SoundBuffer(mutated, format);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }, Util.backgroundExecutor()));
        }

        @Override
        public CompletableFuture<AudioStream> getStream(ResourceLocation path, boolean looping) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    InputStream input = resources.open(path);
                    AudioStream stream = looping ? new net.minecraft.client.sounds.LoopingAudioStream(OggAudioStream::new, input) : new OggAudioStream(input);
                    return mutateStreamIfNeeded(stream, soundFileTargetId(path), ClientCorruptionEffects.current());
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }, Util.backgroundExecutor());
        }

        @Override
        public void clear() {
            corruptedCache.values().forEach(future -> future.thenAccept(SoundBuffer::discardAlBuffer));
            corruptedCache.clear();
        }

        @Override
        public CompletableFuture<?> preload(Collection<Sound> sounds) {
            return CompletableFuture.allOf(sounds.stream()
                    .map(sound -> getCompleteBuffer(sound.getPath()))
                    .toArray(CompletableFuture[]::new));
        }
    }

    private record AudioCacheKey(ResourceLocation path, String signature) {
    }

    private static final class MutatedPcmAudioStream implements AudioStream {
        private final AudioStream delegate;
        private final String targetId;
        private final CorruptionEffectStack stack;
        private long frameCursor;

        private MutatedPcmAudioStream(AudioStream delegate, String targetId, CorruptionEffectStack stack) {
            this.delegate = delegate;
            this.targetId = targetId;
            this.stack = stack;
        }

        @Override
        public AudioFormat getFormat() {
            return delegate.getFormat();
        }

        @Override
        public ByteBuffer read(int bytes) throws IOException {
            ByteBuffer decoded = delegate.read(bytes);
            AudioFormat format = getFormat();
            long firstFrame = frameCursor;
            ByteBuffer mutated = mutateDecodedPcm(decoded, format, targetId, stack, firstFrame);
            int frameSize = format == null || format.getFrameSize() <= 0 ? 2 : format.getFrameSize();
            frameCursor += Math.max(0, mutated.remaining() / frameSize);
            return mutated;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static class MutatedSoundInstance implements SoundInstance {
        protected final SoundInstance delegate;

        MutatedSoundInstance(SoundInstance delegate) {
            this.delegate = delegate;
        }

        @Override
        public ResourceLocation getLocation() {
            return delegate.getLocation();
        }

        @Override
        @Nullable
        public WeighedSoundEvents resolve(SoundManager soundManager) {
            return delegate.resolve(soundManager);
        }

        @Override
        public Sound getSound() {
            return delegate.getSound();
        }

        @Override
        public SoundSource getSource() {
            return delegate.getSource();
        }

        @Override
        public boolean isLooping() {
            return delegate.isLooping();
        }

        @Override
        public boolean isRelative() {
            return delegate.isRelative();
        }

        @Override
        public int getDelay() {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundTargetId(delegate);
            int delay = delegate.getDelay();
            float intensity = audioIntensity(stack, targetId);
            return Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.SOUND_STREAM, targetId + ":delay", delay, 1.0F + intensity * 4.0F, 0.0F, 8.0F, 0x13, soundClock()));
        }

        @Override
        public float getVolume() {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundTargetId(delegate);
            float intensity = audioIntensity(stack, targetId);
            float mutated = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.SOUND_STREAM, targetId + ":volume", delegate.getVolume(), 0.15F + intensity * 1.10F, 0.0F, 1.75F, 0x31, soundClock());
            float minimum = delegate.getVolume() > 0.0F ? Math.min(MAX_SAFE_GAIN, Math.max(0.035F, delegate.getVolume() * 0.22F)) : 0.0F;
            return Math.min(MAX_SAFE_GAIN, Math.max(minimum, mutated));
        }

        @Override
        public float getPitch() {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundTargetId(delegate);
            float intensity = audioIntensity(stack, targetId);
            float multiplier = 1.0F;
            for (CorruptionMutation mutation : stack.mutations(CorruptionSurface.SOUND_STREAM, targetId, 4)) {
                float strength = mutation.strength() * (0.18F + intensity * 0.48F);
                if (mutation.operation() == CorruptionOperation.DAMPEN) {
                    multiplier *= 1.0F - strength * 0.32F;
                } else {
                    multiplier *= 1.0F + strength * (0.28F + mutation.unit(3) * 0.48F);
                }
            }
            multiplier += stack.instability() * intensity * 0.45F;
            return Math.min(2.4F, Math.max(0.55F, delegate.getPitch() * multiplier));
        }

        @Override
        public double getX() {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundTargetId(delegate);
            double span = 0.35D + audioIntensity(stack, targetId) * 4.0D;
            return CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.SOUND_STREAM, targetId + ":x", delegate.getX(), span, delegate.getX() - span, delegate.getX() + span, 0x53, soundClock());
        }

        @Override
        public double getY() {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundTargetId(delegate);
            double span = 0.25D + audioIntensity(stack, targetId) * 2.6D;
            return CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.SOUND_STREAM, targetId + ":y", delegate.getY(), span, delegate.getY() - span, delegate.getY() + span, 0x59, soundClock());
        }

        @Override
        public double getZ() {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundTargetId(delegate);
            double span = 0.35D + audioIntensity(stack, targetId) * 4.0D;
            return CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.SOUND_STREAM, targetId + ":z", delegate.getZ(), span, delegate.getZ() - span, delegate.getZ() + span, 0x61, soundClock());
        }

        @Override
        public Attenuation getAttenuation() {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundTargetId(delegate);
            float intensity = audioIntensity(stack, targetId);
            if (intensity > 0.55F && CorruptionValueMutator.decision(stack, CorruptionSurface.SOUND_STREAM, targetId + ":attenuation", 0x73, 0.06F + intensity * 0.10F)) {
                return Attenuation.NONE;
            }
            return delegate.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return delegate.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = soundTargetId(delegate);
            float intensity = audioIntensity(stack, targetId);
            if (intensity > 0.78F && CorruptionValueMutator.decision(stack, CorruptionSurface.SOUND_STREAM, targetId + ":silent", 0x7D, Math.min(0.03F, (intensity - 0.78F) * 0.08F))) {
                return false;
            }
            return delegate.canPlaySound();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            String targetId = soundTargetId(delegate) + ":" + soundFileTargetId(sound.getPath());
            return delegate.getStream(soundBuffers, sound, looping)
                    .thenApply(stream -> mutateStreamIfNeeded(stream, targetId, ClientCorruptionEffects.current()));
        }
    }

    private static final class MutatedTickableSoundInstance extends MutatedSoundInstance implements TickableSoundInstance {
        private final TickableSoundInstance tickable;

        private MutatedTickableSoundInstance(TickableSoundInstance delegate) {
            super(delegate);
            this.tickable = delegate;
        }

        @Override
        public boolean isStopped() {
            return tickable.isStopped();
        }

        @Override
        public void tick() {
            tickable.tick();
        }
    }
}
