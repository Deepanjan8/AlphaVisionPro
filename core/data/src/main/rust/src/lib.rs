use jni::JNIEnv;
use jni::objects::{JClass, JObject, JValue};
use jni::sys::{jfloat, jint, jlong, jobject, jbyteArray};
use std::os::raw::c_void;

static mut G_BUFFER_POOL_BYTES: usize = 64 * 1024 * 1024;
static mut G_CURRENT_USAGE: i64 = 0;

#[link(name = "jnigraphics")]
extern "C" {
    fn AndroidBitmap_getInfo(
        env: *mut jni::sys::JNIEnv,
        jbitmap: jni::sys::jobject,
        info: *mut AndroidBitmapInfo,
    ) -> i32;

    fn AndroidBitmap_lockPixels(
        env: *mut jni::sys::JNIEnv,
        jbitmap: jni::sys::jobject,
        addrptr: *mut *mut c_void,
    ) -> i32;

    fn AndroidBitmap_unlockPixels(
        env: *mut jni::sys::JNIEnv,
        jbitmap: jni::sys::jobject,
    ) -> i32;
}

#[repr(C)]
#[derive(Debug, Default)]
struct AndroidBitmapInfo {
    width: u32,
    height: u32,
    stride: u32,
    format: i32,
    flags: u32,
}

struct EditParams {
    exposure: f32,
    contrast: f32,
    saturation: f32,
    warmth: f32,
    highlights: f32,
    shadows: f32,
    sharpness: f32,
}

fn clamp8(v: f32) -> u8 {
    v.clamp(0.0, 255.0).round() as u8
}

fn build_exposure_lut(exposure: f32) -> [u8; 256] {
    let ev = exposure * 2.0;
    let gain = 2.0f32.powf(ev);
    let mut lut = [0u8; 256];
    for (i, val) in lut.iter_mut().enumerate() {
        *val = clamp8(i as f32 * gain);
    }
    lut
}

fn build_contrast_lut(contrast: f32) -> [u8; 256] {
    let factor = if contrast >= 0.0 {
        1.0 + contrast
    } else {
        1.0 / (1.0 - contrast)
    };
    let mut lut = [0u8; 256];
    for (i, val) in lut.iter_mut().enumerate() {
        let v = (i as f32 - 128.0) * factor + 128.0;
        *val = clamp8(v);
    }
    lut
}

#[inline]
fn rgb_to_ycbcr(r: u8, g: u8, b: u8) -> (f32, f32, f32) {
    let y  =  0.299 * r as f32 + 0.587 * g as f32 + 0.114 * b as f32;
    let cb = -0.16874 * r as f32 - 0.33126 * g as f32 + 0.5 * b as f32 + 128.0;
    let cr =  0.5 * r as f32 - 0.41869 * g as f32 - 0.08131 * b as f32 + 128.0;
    (y, cb, cr)
}

#[inline]
fn ycbcr_to_rgb(y: f32, cb: f32, cr: f32) -> (u8, u8, u8) {
    let cb_shift = cb - 128.0;
    let cr_shift = cr - 128.0;
    let r = clamp8(y + 1.40200 * cr_shift);
    let g = clamp8(y - 0.34414 * cb_shift - 0.71414 * cr_shift);
    let b = clamp8(y + 1.77200 * cb_shift);
    (r, g, b)
}

#[inline]
fn tone_adjust(luma: f32, shadows: f32, highlights: f32) -> f32 {
    let t = luma / 255.0;
    let shadow_mask = (1.0 - t * 2.0).max(0.0);
    let highlight_mask = (t * 2.0 - 1.0).max(0.0);
    let lift = shadows * shadow_mask * 64.0;
    let drop = highlights * highlight_mask * 64.0;
    (luma + lift + drop).clamp(0.0, 255.0)
}

#[inline]
fn apply_warmth(r: u8, g: u8, b: u8, warmth: f32) -> (u8, u8, u8) {
    let bias = warmth * 20.0;
    (
        clamp8(r as f32 + bias),
        g,
        clamp8(b as f32 - bias),
    )
}

fn process_image_impl(
    src: &[u8],
    dst: &mut [u8],
    width: usize,
    height: usize,
    params: &EditParams,
) {
    let n_pixels = width * height;
    let exposure_lut = build_exposure_lut(params.exposure);
    let contrast_lut = build_contrast_lut(params.contrast);
    let sat_scale = 1.0 + params.saturation;

    for i in 0..n_pixels {
        let src_idx = i * 4;
        let b_in = src[src_idx];
        let g_in = src[src_idx + 1];
        let r_in = src[src_idx + 2];
        let a = src[src_idx + 3];

        let r_exp = exposure_lut[r_in as usize];
        let g_exp = exposure_lut[g_in as usize];
        let b_exp = exposure_lut[b_in as usize];

        let (mut y, mut cb, mut cr) = rgb_to_ycbcr(r_exp, g_exp, b_exp);

        y = tone_adjust(y, params.shadows, params.highlights);
        y = contrast_lut[y.clamp(0.0, 255.0) as usize] as f32;

        cb = (cb - 128.0) * sat_scale + 128.0;
        cr = (cr - 128.0) * sat_scale + 128.0;

        let (r_out, g_out, b_out) = ycbcr_to_rgb(y, cb, cr);
        let (r_warm, g_warm, b_warm) = apply_warmth(r_out, g_out, b_out, params.warmth);

        dst[src_idx] = b_warm;
        dst[src_idx + 1] = g_warm;
        dst[src_idx + 2] = r_warm;
        dst[src_idx + 3] = a;
    }

    if params.sharpness > 0.001 {
        let mut luma_src = vec![0u8; n_pixels];
        for i in 0..n_pixels {
            let idx = i * 4;
            let b_val = dst[idx];
            let g_val = dst[idx + 1];
            let r_val = dst[idx + 2];
            let (y, _, _) = rgb_to_ycbcr(r_val, g_val, b_val);
            luma_src[i] = clamp8(y);
        }

        let mut luma_dst = vec![0u8; n_pixels];
        sharpen_channel(&luma_src, &mut luma_dst, width, height, params.sharpness);

        for i in 0..n_pixels {
            let idx = i * 4;
            let b_val = dst[idx];
            let g_val = dst[idx + 1];
            let r_val = dst[idx + 2];
            let (_, cb, cr) = rgb_to_ycbcr(r_val, g_val, b_val);
            let (r_new, g_new, b_new) = ycbcr_to_rgb(luma_dst[i] as f32, cb, cr);
            dst[idx] = b_new;
            dst[idx + 1] = g_new;
            dst[idx + 2] = r_new;
        }
    }
}

fn sharpen_channel(src: &[u8], dst: &mut [u8], width: usize, height: usize, strength: f32) {
    let k_center = 1.0 + 8.0 * strength;
    let k_edge = -strength;

    // Copy border pixels
    for x in 0..width {
        dst[x] = src[x];
        dst[(height - 1) * width + x] = src[(height - 1) * width + x];
    }
    for y in 0..height {
        dst[y * width] = src[y * width];
        dst[y * width + width - 1] = src[y * width + width - 1];
    }

    for y in 1..(height - 1) {
        for x in 1..(width - 1) {
            let idx = y * width + x;
            let val = k_center * src[idx] as f32
                + k_edge * src[idx - 1] as f32
                + k_edge * src[idx + 1] as f32
                + k_edge * src[idx - width] as f32
                + k_edge * src[idx + width] as f32
                + k_edge * src[idx - width - 1] as f32
                + k_edge * src[idx - width + 1] as f32
                + k_edge * src[idx + width - 1] as f32
                + k_edge * src[idx + width + 1] as f32;
            dst[idx] = clamp8(val);
        }
    }
}

fn strip_exif_impl(buf: &mut [u8]) {
    let len = buf.len();
    let mut i = 0;
    while i < len.saturating_sub(3) {
        if buf[i] == 0xFF && buf[i + 1] == 0xE1 {
            let seg_len = ((buf[i + 2] as usize) << 8) | (buf[i + 3] as usize);
            buf[i + 1] = 0xFE;
            buf[i + 2] = 0x00;
            buf[i + 3] = 0x02;
            let start = i + 4;
            let end = (i + 2 + seg_len).min(len);
            if start < end {
                buf[start..end].fill(0);
            }
            i += 2 + seg_len;
        } else {
            i += 1;
        }
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_processImage(
    mut env: JNIEnv,
    _class: JClass,
    src_bitmap: JObject,
    exposure: jfloat,
    contrast: jfloat,
    saturation: jfloat,
    warmth: jfloat,
    highlights: jfloat,
    shadows: jfloat,
    sharpness: jfloat,
) -> jobject {
    let raw_env = env.get_native_interface();
    let mut info = AndroidBitmapInfo::default();
    if AndroidBitmap_getInfo(raw_env, src_bitmap.as_raw(), &mut info) < 0 {
        return std::ptr::null_mut();
    }

    let bmp_class = match env.find_class("android/graphics/Bitmap") {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };
    let config_class = match env.find_class("android/graphics/Bitmap$Config") {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };
    let argb_fid = match env.get_static_field_id(&config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;") {
        Ok(id) => id,
        Err(_) => return std::ptr::null_mut(),
    };
    let argb_config = match env.get_static_field(&config_class, argb_fid, "Landroid/graphics/Bitmap$Config;") {
        Ok(val) => match val.l() {
            Ok(obj) => obj,
            Err(_) => return std::ptr::null_mut(),
        },
        Err(_) => return std::ptr::null_mut(),
    };

    let create_mid = match env.get_static_method_id(
        &bmp_class,
        "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;",
    ) {
        Ok(id) => id,
        Err(_) => return std::ptr::null_mut(),
    };

    let dst_bitmap = match env.call_static_method_unchecked(
        &bmp_class,
        create_mid,
        jni::signature::ReturnType::Object,
        &[
            JValue::Int(info.width as i32).to_jni(),
            JValue::Int(info.height as i32).to_jni(),
            JValue::Object(&argb_config).to_jni(),
        ],
    ) {
        Ok(val) => match val.l() {
            Ok(obj) => obj,
            Err(_) => return std::ptr::null_mut(),
        },
        Err(_) => return std::ptr::null_mut(),
    };

    let mut src_pixels: *mut c_void = std::ptr::null_mut();
    let mut dst_pixels: *mut c_void = std::ptr::null_mut();

    if AndroidBitmap_lockPixels(raw_env, src_bitmap.as_raw(), &mut src_pixels) < 0 {
        return std::ptr::null_mut();
    }
    if AndroidBitmap_lockPixels(raw_env, dst_bitmap.as_raw(), &mut dst_pixels) < 0 {
        AndroidBitmap_unlockPixels(raw_env, src_bitmap.as_raw());
        return std::ptr::null_mut();
    }

    let width = info.width as usize;
    let height = info.height as usize;
    let n_bytes = width * height * 4;

    let src_slice = std::slice::from_raw_parts(src_pixels as *const u8, n_bytes);
    let dst_slice = std::slice::from_raw_parts_mut(dst_pixels as *mut u8, n_bytes);

    let params = EditParams {
        exposure,
        contrast,
        saturation,
        warmth,
        highlights,
        shadows,
        sharpness,
    };

    G_CURRENT_USAGE = (n_bytes * 2) as i64;

    process_image_impl(src_slice, dst_slice, width, height, &params);

    AndroidBitmap_unlockPixels(raw_env, src_bitmap.as_raw());
    AndroidBitmap_unlockPixels(raw_env, dst_bitmap.as_raw());

    dst_bitmap.into_raw()
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_stripExifNative(
    mut env: JNIEnv,
    _class: JClass,
    jpeg_bytes: jbyteArray,
) -> jbyteArray {
    let len = env.get_array_length(&jpeg_bytes).unwrap_or(0);
    if len <= 0 {
        return jpeg_bytes;
    }

    let mut buf = vec![0i8; len as usize];
    if env.get_byte_array_region(&jpeg_bytes, 0, &mut buf).is_err() {
        return jpeg_bytes;
    }

    {
        let u8_slice = std::slice::from_raw_parts_mut(buf.as_mut_ptr() as *mut u8, len as usize);
        strip_exif_impl(u8_slice);
    }

    let result = match env.new_byte_array(len) {
        Ok(arr) => arr,
        Err(_) => return jpeg_bytes,
    };
    if env.set_byte_array_region(&result, 0, &buf).is_err() {
        return jpeg_bytes;
    }

    result.into_raw()
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_getNativeMemoryUsage(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    G_CURRENT_USAGE
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_setBufferPoolSizeMb(
    _env: JNIEnv,
    _class: JClass,
    size_mb: jint,
) {
    G_BUFFER_POOL_BYTES = (size_mb as usize) * 1024 * 1024;
}
