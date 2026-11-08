package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

/**
 * Editor-only localizer. Translations live in assets/bbsanimationeditor/lang,
 * while the original English UI labels are stable lookup aliases. This allows
 * existing BBS/Blockbuster widgets to gain Russian text without changing
 * their serialized keys, enum values, filenames, or editor.dat schema.
 *
 * Only exact known UI labels and explicit dynamic prefixes are translated.
 * Arbitrary scene names / file names / shader channels are left intact.
 */
public final class EditorLanguage
{
    private static final Map<String,String> ALIASES=new HashMap<String,String>();
    private static final List<String> PREFIXES=new ArrayList<String>();

    static
    {
        add("editor.title", "BBS Animation Editor");
        add("actors", "ACTORS");
        add("actor.none", "No actors");
        add("actor.generic", "Actor");
        add("actor.unnamed", "Unnamed Actor");
        add("scene.select", "Select Scene");
        add("scene.missing", "No scenes found");
        add("actor.unnamed_short", "Unnamed");
        add("scene.prefix", "SCENE: ");
        add("actor.prefix", "ACTOR: ");
        add("actor.prefix_none", "ACTOR: —");
        add("theme.prefix", "THEME: ");
        add("mode.prefix", "MODE: ");
        add("frame.prefix", "FRAME  ");
        add("timeline", "TIMELINE");
        add("timeline.scene", "Scene");
        add("timeline.frames", "frames");
        add("preview", "PREVIEW");
        add("preview.small", "Preview too small");
        add("preview.bones", "BODY PART BONES");
        add("preview.no_bones", "No bones");
        add("preview.all_actors", "ALL ACTORS");
        add("preview.selected", "SELECTED ONLY");
        add("preview.shot", "Shot camera (switch to free view)");
        add("preview.free_shot", "Free camera (switch to shot view)");
        add("preview.stop_record", "Stop recording selected camera key");
        add("preview.record", "Record camera movement to selected clip key");
        add("preview.focus", "Auto-focus selected key");
        add("preview.orbit", "Orbit camera (switch to free)");
        add("preview.free_orbit", "Free camera (switch to orbit)");
        add("preview.disable_film", "Disable film camera view");
        add("preview.camera_clips", "View through CAMERA clips");
        add("preview.actor_first", "Selected actor rendered FIRST");
        add("preview.actor_last", "Selected actor rendered LAST");
        add("preview.shadow_experimental", "ACTOR SUN SHADOWS: EXPERIMENTAL (Ctrl+Shift+F7)");
        add("audio.sound_on", "WAV SOUND ON");
        add("audio.muted", "WAV MUTED");
        add("audio.monitor_no_file", "WAV MONITOR: NO FILE");
        add("audio.monitor_on", "WAV MONITOR: ON (click to mute)");
        add("audio.monitor_muted", "WAV MONITOR: MUTED (click to hear)");
        add("audio.sound", "WAV SOUND");
        add("interpolation.heading", "INTERPOLATION");
        add("interpolation.easing", "Easing");
        add("interpolation.constant", "Constant");
        add("interpolation.linear", "Linear");
        add("interpolation.quadratic", "Quadratic");
        add("interpolation.cubic", "Cubic");
        add("interpolation.hermite", "Hermite");
        add("interpolation.exponential", "Exponential");
        add("interpolation.bezier", "Bezier");
        add("interpolation.back", "Back");
        add("interpolation.elastic", "Elastic");
        add("interpolation.bounce", "Bounce");
        add("interpolation.sine", "Sine");
        add("interpolation.quartic", "Quartic");
        add("interpolation.quintic", "Quintic");
        add("interpolation.circle", "Circle");
        add("interpolation.in", "In");
        add("interpolation.out", "Out");
        add("interpolation.inout", "In Out");
        add("transform", "TRANSFORM");
        add("transform.title", "Transform");
        add("transform.position", "POSITION");
        add("transform.position_tc", "Position");
        add("transform.rotation", "ROTATION");
        add("transform.rotation_tc", "Rotation");
        add("transform.scale", "SCALE");
        add("transform.scale_tc", "Scale");
        add("transform.key.prefix", "Key ");
        add("camera", "CAMERA");
        add("camera.clips", "CLIPS");
        add("camera.lights", "LIGHTS");
        add("camera.mirrors", "MIRRORS");
        add("camera.layer.prefix", "Layer ");
        add("camera.preset_saved", "SAVED PRESETS");
        add("camera.preset_hint", "RMB clip to save");
        add("camera.save_as_preset", "Save as Preset");
        add("camera.save_preset", "SAVE CAMERA PRESET");
        add("camera.add_clip", "ADD CAMERA CLIP");
        add("camera.overwrite", "OVERWRITE");
        add("camera.modifiers", "MODIFIERS");
        add("camera.misc", "MISC");
        add("camera.track_actor", "TRACK AN ACTOR");
        add("camera.media_empty", "No matching files. Enter a path manually.");
        add("camera.clip_view", "View");
        add("camera.clip_dolly", "Dolly");
        add("camera.clip_path", "Path");
        add("camera.clip_keyframe", "Keyframe");
        add("camera.clip_tracker", "Tracker");
        add("camera.clip_spline", "Spline");
        add("camera.clip_translate", "Translate");
        add("camera.clip_angle", "Angle");
        add("camera.clip_drag", "Drag");
        add("camera.clip_shake", "Shake");
        add("camera.clip_math", "Math");
        add("camera.clip_remapper", "Remapper");
        add("camera.clip_look", "Look");
        add("camera.clip_orbit", "Orbit");
        add("camera.clip_dollyzoom", "Dolly Zoom");
        add("camera.clip_audio", "Audio");
        add("camera.clip_curve", "Curve");
        add("camera.clip_subtitle", "Subtitle");
        add("camera.clip_image", "Image");
        add("camera.clip_video", "Video");
        add("camera.inspector_channel", "Channel");
        add("camera.inspector_curve_channels", "CURVE CHANNELS");
        add("camera.inspector_rescan", "RESCAN");
        add("camera.inspector_pending.prefix", "Pending: ");
        add("camera.inspector_on", "On");
        add("camera.inspector_off", "Off");
        add("camera.inspector_on_up", "ON");
        add("camera.inspector_off_up", "OFF");
        add("camera.inspector_active_channels", "ACTIVE CHANNELS");
        add("camera.inspector_key_prev", "< key");
        add("camera.inspector_key_next", "key >");
        add("camera.inspector_delete", "Delete");
        add("camera.inspector_path_points", "PATH POINTS");
        add("camera.inspector_keyframe_channels", "KEYFRAME CHANNELS");
        add("camera.inspector_keys.prefix", "Keys: ");
        add("camera.inspector_add_key", "Add key at playhead");
        add("camera.inspector_selected_key", "SELECTED KEY");
        add("camera.inspector_frame", "Frame");
        add("camera.inspector_mask", "Channel mask");
        add("camera.inspector_key_interpolation", "Key interpolation");
        add("camera.inspector_value", "Value");
        add("camera.inspector_appearance", "APPEARANCE");
        add("camera.inspector_enabled", "Enabled");
        add("camera.inspector_mode", "Mode");
        add("camera.inspector_fadein", "Fade in");
        add("camera.inspector_fadeout", "Fade out");
        add("camera.inspector_before", "Before");
        add("camera.inspector_after", "After");
        add("camera.inspector_placement", "PLACEMENT");
        add("camera.inspector_anchor", "Anchor");
        add("camera.inspector_width", "Width");
        add("camera.inspector_height", "Height");
        add("camera.inspector_opacity", "Opacity");
        add("camera.inspector_rotation", "Rotation");
        add("camera.inspector_scalex", "Scale X");
        add("camera.inspector_scaley", "Scale Y");
        add("camera.inspector_media_animation", "MEDIA ANIMATION");
        add("camera.inspector_transform_keys.prefix", "Transform keys: ");
        add("camera.inspector_media_key", "Add media key at playhead");
        add("camera.inspector_selected_media_key", "SELECTED MEDIA KEY");
        add("camera.inspector_distance", "Distance");
        add("camera.inspector_reverse", "Reverse");
        add("camera.inspector_position_interpolation", "Position interpolation");
        add("camera.inspector_angle_interpolation", "Angle interpolation");
        add("camera.inspector_additive", "Additive");
        add("camera.inspector_target", "TARGET");
        add("camera.inspector_entity", "Entity");
        add("camera.inspector_group", "Group");
        add("camera.inspector_offset", "OFFSET");
        add("camera.inspector_look_at", "Look at");
        add("camera.inspector_relative", "Relative");
        add("camera.inspector_progress", "PROGRESS");
        add("camera.inspector_start", "Start");
        add("camera.inspector_end", "End");
        add("camera.inspector_camera_local", "Camera-local coordinates");
        add("camera.inspector_fov_delta", "FOV delta");
        add("camera.inspector_interpolation", "INTERPOLATION");
        add("camera.inspector_clip_props", "CLIP PROPERTIES");
        add("camera.inspector_select_clip", "Select a camera clip");
        add("camera.inspector_add_media", "Add media key at playhead");
        add("camera.inspector_active", "ACTIVE");
        add("camera.inspector_yaw", "Yaw");
        add("camera.inspector_pitch", "Pitch");
        add("camera.inspector_roll", "Roll");
        add("camera.inspector_fov", "FOV");
        add("mirrors.add", "+ ADD");
        add("mirrors.delete", "DELETE");
        add("mirrors.title", "SCENE MIRRORS");
        add("mirrors.none", "No mirrors yet");
        add("mirrors.properties", "MIRROR PROPERTIES");
        add("mirrors.create", "Add a mirror to the scene");
        add("mirrors.rectangle", "WORLD RECTANGLE");
        add("mirrors.yaw", "YAW (DEG)");
        add("mirrors.width", "WIDTH");
        add("mirrors.height", "HEIGHT");
        add("mirrors.move", "MOVE XYZ");
        add("mirrors.rotate", "ROTATE Y");
        add("mirrors.yaw_guide", "Drag the Yaw ring in Preview");
        add("mirrors.move_guide", "Drag XYZ handles (or mirror face)");
        add("mirrors.static", "Static scene object; no keys");
        add("mirrors.limit", "Max 32 / reflection view: 1");
        add("lights.remove", "REMOVE");
        add("lights.title", "SCENE LIGHTS");
        add("lights.none", "No lights yet");
        add("lights.properties", "LIGHT PROPERTIES");
        add("lights.create", "Create a static light");
        add("lights.origin", "ORIGIN");
        add("lights.target", "TARGET");
        add("lights.light", "LIGHT");
        add("lights.world_origin", "WORLD ORIGIN");
        add("lights.world_target", "WORLD TARGET");
        add("lights.drag_gizmo", "Drag 3D gizmo to reposition");
        add("lights.source", "SOURCE");
        add("lights.keys_off", "No keys - saved with the scene");
        add("lights.not_same", "Origin and Target cannot coincide");
        add("lights.badcoord", "Invalid world coordinate");
        add("lights.light.prefix", "Light ");
        add("parts.title", "BODY PARTS");
        add("parts.doubleclick", "Double-click an actor-bone row to create.");
        add("parts.model", "MODEL");
        add("parts.global", "GLOBAL TRANSFORM");
        add("parts.select", "SELECT MODEL");
        add("parts.remove", "REMOVE ATTACHMENT");
        add("parts.weld.prefix", "WELD GEOMETRY: ");
        add("parts.weld_chameleon", "WELD: CHAMELEON ONLY");
        add("parts.back", "< BACK TO PARTS");
        add("parts.model.prefix", "MODEL: ");
        add("parts.physics", "PHYSICS");
        add("parts.chains", "CHAINS");
        add("parts.colliders", "COLLIDERS");
        add("parts.enablecollider", "Enable collider");
        add("parts.shape", "Shape");
        add("parts.shapescale", "Shape scale");
        add("parts.static", "Static passive geometry");
        add("parts.excludehair", "Hair-driven bones excluded");
        add("parts.root", "Enable chain root");
        add("parts.gravity", "Gravity");
        add("parts.stiffness", "Stiffness");
        add("parts.damping", "Damping");
        add("parts.body_collisions", "Body collisions");
        add("parts.chainbones.prefix", "Chain bones: ");
        add("parts.show_jolt", "Show Jolt colliders");
        add("parts.solver_disabled", "Solver: Jolt disabled");
        add("character.title", "CHARACTER");
        add("character.appearance", "APPEARANCE");
        add("character.animsetup", "ANIMATION SETUP");
        add("character.overrides", "BODY PART OVERRIDES");
        add("character.animparams", "ANIMATION PARAMETERS");
        add("character.ragdoll", "RAGDOLL");
        add("character.ragdoll.bodies", "BOUND BODIES");
        add("character.ragdoll.influence", "PHYSICS INFLUENCE");
        add("character.ragdoll.key_add", "ADD KEY");
        add("character.ragdoll.key_update", "UPDATE KEY");
        add("character.ragdoll.select", "SELECT BB / EMOTICONS");
        add("character.ragdoll.no_rig", "NO RAGDOLL RIG");
        add("character.ragdoll.incomplete", "RIG INCOMPLETE");
        add("character.ragdoll.check_bones", "CHECK MODEL BONES");
        add("character.ragdoll.next_stage", "JOLT BODY SOLVER: NEXT STAGE");
        add("character.lipsync", "LIP SYNC");
        add("character.textkeys", "TEXT TO KEYS");
        add("character.partselect", "SELECT PART");
        add("character.audiokeys", "AUDIO TO KEYS");
        add("character.loadwav", "LOAD WAV");
        add("character.loadwav.prefix", "LOAD WAV: ");
        add("character.not_loaded", "Not loaded");
        add("character.default", "Default");
        add("character.no_model", "No model");
        add("character.morphkey", "MORPH KEY");
        add("character.skinkey", "SKIN KEY");
        add("character.loaded", "Loaded");
        add("character.morph", "Morph");
        add("character.skin", "Skin");
        add("character.record", "Record");
        add("character.frames", "Frames");
        add("character.current", "Current");
        add("character.select_key", "Select Character key");
        add("character.in_timeline", "in Character Timeline");
        add("character.no_bones", "No bones found");
        add("character.bones", "Bones");
        add("character.anim_idle", "Idle");
        add("character.emoticons_required", "Animation Setup requires");
        add("character.emoticons_char", "an Emoticons character");
        add("character.actions", "Actions");
        add("character.animation", "Animation");
        add("character.animation.prefix", "Animation: ");
        add("character.speed", "Speed");
        add("character.fade", "Fade");
        add("character.tick", "Tick");
        add("character.clamp", "Clamp");
        add("character.reset", "Reset");
        add("character.main", "Main Character");
        add("character.track.prefix", "Track ");
        add("character.character_track.prefix", "Character Track ");
        add("audio.wavlibrary", "WAV LIBRARY");
        add("audio.selecttimeline", "Select timeline audio");
        add("audio.selectwav", "Select a WAV file");
        add("audio.refresh", "Refresh");
        add("audio.loadselected", "Load selected");
        add("audio.back", "Back");
        add("audio.putfiles", "Put WAV files in the audio folder, then Refresh");
        add("audio.wav_files.prefix", " WAV file(s) found");
        add("audio.cannotload", "Could not load WAV");
        add("audio.text_generate", "Generate");
        add("audio.text_heading", "TEXT TO KEYS");
        add("audio.text_desc", "Generate mouth keys from text");
        add("audio.startframe", "Start frame");
        add("audio.wpm", "Words per minute (20-600)");
        add("audio.idletexture", "Idle texture (full resource path)");
        add("audio.analyze", "Analyze + Generate");
        add("audio.audio_heading", "AUDIO TO KEYS");
        add("audio.output_texture", "Output: TEXTURE (Skin keys)");
        add("audio.output_chameleon", "Output: CHAMELEON (Character keys)");
        add("audio.python", "Python executable");
        add("audio.noaudio", "No audio");
        add("audio.load_first", "Load WAV in LIP SYNC first");
        add("audio.badframe", "Invalid start frame");
        add("audio.analyzing", "Analyzing WAV with offline Vosk...");
        add("audio.recognition_failed", "Recognition failed");
        add("audio.recognized.prefix", "Recognized ");
        add("audio.wordscreated", " words, created ");
        add("audio.keys", " keys");
        add("audio.failed", "Key generation failed");
        add("video.export", "EXPORT VIDEO");
        add("video.rendering", "Rendering film...");
        add("video.framescaptured.prefix", "Frames captured: ");
        add("video.encoded.prefix", "Encoded: ");
        add("video.cancel", "CANCEL EXPORT");
        add("video.resolution", "Resolution");
        add("video.quality", "Encoding quality");
        add("video.ffmpeg", "FFmpeg executable (ffmpeg or absolute path)");
        add("video.savemp4", "Save MP4 as");
        add("video.video.prefix", "VIDEO: ");
        add("video.startrender", "START RENDER");
        add("video.close", "CLOSE");
        add("video.captured.prefix", "    Captured: ");
        add("video.canceled", "MP4 export canceled");
        add("video.even", "Use even dimensions from 128 to 8192");
        add("video.enterffmpeg", "Enter FFmpeg executable location");
        add("video.outputmp4", "Output file must end in .mp4");
        add("video.fileexists", "File already exists; choose another name");
        add("delete.heading", "DELETE KEYFRAME");
        add("delete.prefix", "Delete keyframe at frame ");
        add("delete.cancel", "Cancel");
        add("delete.delete", "Delete");
        add("light.name", "Name");
        add("light.click", "Click value to edit");
        add("light.settings_empty", "No settings in this section");
        add("light.alpha.prefix", "Alpha ");
        add("light.unavailable", "BetterLights unavailable");
        add("light.native_unavailable", "Native BetterLights properties unavailable");
        add("light.enter_bool", "Enter true or false");
        add("light.enter_rgb", "Enter #RRGGBB or #AARRGGBB");
        add("light.invalid_number", "Not a finite value");
        add("light.value.prefix", "Light value: ");
        add("light.betterlights", "BETTERLIGHTS");
        PREFIXES.add("SCENE: ");
        PREFIXES.add("ACTOR: ");
        PREFIXES.add("THEME: ");
        PREFIXES.add("MODE: ");
        PREFIXES.add("FRAME  ");
        PREFIXES.add("Key ");
        PREFIXES.add("Layer ");
        PREFIXES.add("Pending: ");
        PREFIXES.add("Keys: ");
        PREFIXES.add("Transform keys: ");
        PREFIXES.add("Light ");
        PREFIXES.add("WELD GEOMETRY: ");
        PREFIXES.add("MODEL: ");
        PREFIXES.add("Chain bones: ");
        PREFIXES.add("LOAD WAV: ");
        PREFIXES.add("Animation: ");
        PREFIXES.add("Track ");
        PREFIXES.add("Character Track ");
        PREFIXES.add(" WAV file(s) found");
        PREFIXES.add("Recognized ");
        PREFIXES.add("Frames captured: ");
        PREFIXES.add("Encoded: ");
        PREFIXES.add("VIDEO: ");
        PREFIXES.add("    Captured: ");
        PREFIXES.add("Delete keyframe at frame ");
        PREFIXES.add("Alpha ");
        PREFIXES.add("Light value: ");
        add("mode.character", "Character");
        add("mode.camera", "Camera");
        add("mode.pose", "Pose");
        add("mode.bodyparts", "Body Parts");
        add("theme.emerald", "Emerald");
        add("theme.ocean", "Ocean");
        add("theme.ruby", "Ruby");
        add("theme.amethyst", "Amethyst");
        add("output.format", "OUTPUT FORMAT");
        add("output.custom_resolution", "Custom resolution...");
        add("output.custom_size", "Custom output size");
        add("output.apply", "Apply");
        add("output.even", "Even dimensions; 128-8192 px");
        add("output.frame_preview", "FRAME PREVIEW");
        add("output.bad_size", "Use even dimensions (128-8192)");
        add("output.bad_number", "Enter numeric dimensions.");
        add("camera.shake", "SHAKE");
        add("camera.shake_amount", "Shake amount");
        add("camera.noise", "Noise");
        add("camera.look_block", "LOOK AT BLOCK");
        add("camera.atblock", "At block");
        add("camera.block", "BLOCK");
        add("camera.forward", "Forward");
        add("camera.orbit", "ORBIT");
        add("camera.absolute", "Absolute");
        add("camera.copy", "Copy");
        add("camera.center", "CENTER");
        add("camera.use_fixed", "Use fixed center");
        add("camera.drag", "DRAG");
        add("camera.deterministic", "Deterministic");
        add("camera.factor", "Factor");
        add("camera.rate", "Rate");
        add("camera.dollyzoom", "DOLLY ZOOM");
        add("camera.focus", "Focus distance");
        add("camera.startfov", "Start FOV");
        add("camera.endfov", "End FOV");
        add("camera.expression", "EXPRESSION");
        add("camera.expression_value", "Expression");
        add("camera.remap", "REMAP TIME");
        add("camera.time_remapping", "TIME REMAPPING");
        add("camera.source_start", "Source start tick");
        add("camera.source_end", "Source end tick");
        add("camera.curve_channel", "CURVE CHANNEL");
        add("camera.curve_keys", "CURVE KEYS");
        add("camera.shader_value", "Shader option value");
        add("camera.film_delta", "Film delta (-1..1)");
        add("camera.texture", "Texture");
        add("camera.browse_images", "Browse images");
        add("camera.fullscreen", "Fullscreen");
        add("camera.mirror_x", "Mirror horizontally");
        add("camera.mirror_y", "Mirror vertically");
        add("camera.smooth", "Smooth");
        add("camera.tint", "Tint");
        add("camera.audio_file", "Audio file");
        add("camera.browse_audio", "Browse audio");
        add("camera.offset", "Offset");
        add("camera.volume", "Volume");
        add("camera.extend_audio", "Extend to audio length");
        add("camera.video_file", "Video file");
        add("camera.browse_video", "Browse videos");
        add("camera.audio_track", "Audio track");
        add("camera.extend_video", "Extend to video length");
        add("camera.loop", "Loop");
        add("camera.text", "Text");
        add("camera.color", "Color");
        add("camera.text_shadow", "Text shadow");
        add("camera.background", "Background");
        add("camera.background_offset", "Background offset");
        add("camera.shadow", "Shadow");
        add("camera.opaque_shadow", "Opaque shadow");
        add("camera.font_heading", "FONT");
        add("camera.font_file", "Font file");
        add("camera.browse_fonts", "Browse fonts");
        add("camera.font_size", "Font size");
        add("camera.line_height", "Line height");
        add("camera.max_width", "Max width");
        add("camera.image_file", "Image file");
        add("camera.image_right", "Image right");
        add("camera.image_scale", "Image scale");
        add("camera.general", "GENERAL");
        add("camera.title", "Title");
        add("camera.duration", "Duration");
        add("camera.layer", "Layer");
        add("camera.start", "Start");
        add("camera.end", "End");
        add("camera.fov", "FOV delta");
        add("camera.key.label", "KEYFRAME");
        add("camera.spline.label", "SPLINE");
        add("camera.translate.label", "TRANSLATE");
        add("video.crf_high", "High (CRF 18)");
        add("video.crf_balanced", "Balanced (22)");
        add("video.crf_smaller", "Smaller (26)");
        // Remaining fixed UI labels found in the main editor panels.
        // Keep filenames, morph names and shader identifiers untranslated.
        add("light_pick_key", "Select a key on Origin / Target");
        add("media_prefix", "MEDIA / ");
        add("media_location", "config/bbs-editor/media (ESC to close)");
        add("mirror_yaw", "YAW");
        add("world_coordinates", "WORLD COORDINATES / BLOCKS");
        add("no_wav_part", "NO WAV / PART");
        add("xyz_blocks", "XYZ offset / blocks");
        PREFIXES.add("MEDIA / ");
        Collections.sort(PREFIXES,new Comparator<String>()
        {
            @Override public int compare(String a,String b)
            {
                return Integer.compare(b.length(),a.length());
            }
        });
    }

    private EditorLanguage(){}

    private static void add(String id,String label)
    {
        ALIASES.put(label,"bbsanimationeditor.ui."+id);
    }

    /** No language option inside the editor: honor Minecraft's locale. */
    public static boolean isRussian()
    {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc==null||mc.gameSettings==null)return false;
        String language=mc.gameSettings.language;
        return language!=null&&language.toLowerCase(Locale.ROOT).startsWith("ru_");
    }

    private static String lookup(String original)
    {
        String key=ALIASES.get(original);
        if(key==null)return original;

        // Use this mod's UTF-8 ru_ru catalog, not Minecraft's global
        // English fallback. In 1.12.2 I18n.format(key) can return the
        // English label even when gameSettings.language is "ru_ru".
        // This is why the Cyrillic font was correct while ACTORS,
        // TIMELINE, TRANSFORM and other UI labels remained English.
        String russian=EditorLanguageCatalog.russian(key);
        if(russian!=null)return russian;

        // Resource-pack or custom keys are still allowed if no built-in
        // translation exists. Do not mistake an untranslated key for text.
        String translated=I18n.format(key);
        return key.equals(translated)?original:translated;
    }

    private static String formatRussian(String key,Object...args)
    {
        String template=EditorLanguageCatalog.russian(key);
        if(template!=null)
        {
            try
            {
                return String.format(Locale.ROOT,template,args);
            }
            catch(java.util.IllegalFormatException invalid)
            {
                System.err.println("[BBS Editor][Locale] Invalid format for "
                        +key+": "+invalid.getMessage());
            }
        }
        return I18n.format(key,args);
    }

    public static String tr(String english)
    {
        if(english==null||english.isEmpty()||!isRussian())return english;
        String exact=lookup(english);
        if(!english.equals(exact))return exact;
        // A few status messages insert numbers between literal fragments.
        // Translate their entire templates rather than rewriting filenames
        // or inserting Russian text into arbitrary user-provided fields.
        final String wavSuffix=" WAV file(s) found";
        if(english.endsWith(wavSuffix))
        {
            String count=english.substring(0,english.length()-wavSuffix.length());
            if(count.matches("[0-9]+"))
                return formatRussian(
                        "bbsanimationeditor.ui.audio.found_count",count);
        }
        final String generatedPrefix="Generated ";
        final String generatedSuffix=" mouth texture keys";
        if(english.startsWith(generatedPrefix)&&
                english.endsWith(generatedSuffix))
        {
            String count=english.substring(generatedPrefix.length(),
                    english.length()-generatedSuffix.length());
            if(count.matches("[0-9]+"))
                return formatRussian(
                        "bbsanimationeditor.ui.audio.generated_count",count);
        }
        if(english.startsWith("Recognized ")&&
                english.endsWith(" keys")&&
                english.contains(" words, created "))
        {
            java.util.regex.Matcher parts=java.util.regex.Pattern.compile(
                    "^Recognized ([0-9]+) words, created ([0-9]+) keys$")
                    .matcher(english);
            if(parts.matches())
                return formatRussian(
                        "bbsanimationeditor.ui.audio.recognized_count",
                        parts.group(1),parts.group(2));
        }
        for(String prefix:PREFIXES)
            if(english.startsWith(prefix)&&english.length()>prefix.length())
            {
                String translated=lookup(prefix);
                if(!prefix.equals(translated))
                {
                    String suffix=english.substring(prefix.length());
                    // The value after THEME:/MODE: is a fixed UI enum, not
                    // a user file/morph name. Translate it as well; leave
                    // SCENE:/ACTOR: values exactly as they were authored.
                    if("THEME: ".equals(prefix)||"MODE: ".equals(prefix))
                        suffix=lookup(suffix);
                    return translated+
                            (prefix.endsWith(" ")&&!translated.endsWith(" ")?
                                    " ":"")+
                            suffix;
                }
            }
        return english;
    }
}
