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
    let argb_config = match env.get_static_field(&config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;") {
        Ok(val) => match val.l() {
            Ok(obj) => obj,
            Err(_) => return std::ptr::null_mut(),
        },
        Err(_) => return std::ptr::null_mut(),
    };

    let dst_bitmap = match env.call_static_method(
        &bmp_class,
        "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;",
        &[
            JValue::Int(info.width as i32),
            JValue::Int(info.height as i32),
            JValue::Object(&argb_config),
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
    let array = jni::objects::JByteArray::from_raw(jpeg_bytes);
    let len = env.get_array_length(&array).unwrap_or(0);
    if len <= 0 {
        return jpeg_bytes;
    }

    let mut buf = vec![0i8; len as usize];
    if env.get_byte_array_region(&array, 0, &mut buf).is_err() {
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

// --- EXIF Parsing Logic ---

#[derive(Clone, Copy, Debug, PartialEq)]
enum Endian {
    Little,
    Big,
}

impl Endian {
    fn read_u16(&self, bytes: &[u8], offset: usize) -> Option<u16> {
        let end = offset.checked_add(2)?;
        let b = bytes.get(offset..end)?;
        match self {
            Endian::Little => Some(u16::from_le_bytes([b[0], b[1]])),
            Endian::Big => Some(u16::from_be_bytes([b[0], b[1]])),
        }
    }

    fn read_u32(&self, bytes: &[u8], offset: usize) -> Option<u32> {
        let end = offset.checked_add(4)?;
        let b = bytes.get(offset..end)?;
        match self {
            Endian::Little => Some(u32::from_le_bytes([b[0], b[1], b[2], b[3]])),
            Endian::Big => Some(u32::from_be_bytes([b[0], b[1], b[2], b[3]])),
        }
    }

    fn read_rational(&self, bytes: &[u8], offset: usize) -> Option<(u32, u32)> {
        let num = self.read_u32(bytes, offset)?;
        let den = self.read_u32(bytes, offset.checked_add(4)?)?;
        Some((num, den))
    }
}

struct ParsedMetadata {
    make: Option<String>,
    model: Option<String>,
    focal_length: Option<String>,
    aperture: Option<String>,
    shutter_speed: Option<String>,
    iso: Option<String>,
    gps_latitude: Option<f64>,
    gps_longitude: Option<f64>,
    date_taken: Option<String>,
    orientation: i32,
}

fn find_exif_data(jpeg: &[u8]) -> Option<&[u8]> {
    if jpeg.len() < 4 {
        return None;
    }
    if jpeg[0] != 0xFF || jpeg[1] != 0xD8 {
        return None;
    }
    let mut offset = 2;
    while offset + 4 <= jpeg.len() {
        if jpeg[offset] != 0xFF {
            offset += 1;
            continue;
        }
        let marker = jpeg[offset + 1];
        if marker == 0xD9 {
            break;
        }
        let has_length = match marker {
            0x00 | 0xD0..=0xD7 | 0xD8 | 0xD9 => false,
            _ => true,
        };
        if has_length {
            let length = u16::from_be_bytes([jpeg[offset + 2], jpeg[offset + 3]]) as usize;
            if marker == 0xE1 {
                let start = offset + 4;
                let end = offset.checked_add(2)?.checked_add(length)?;
                if end <= jpeg.len() && end >= start {
                    let segment = &jpeg[start..end];
                    if segment.len() > 6 && &segment[0..6] == b"Exif\0\0" {
                        return Some(&segment[6..]);
                    }
                }
            }
            offset = offset.checked_add(2)?.checked_add(length)?;
        } else {
            offset = offset.checked_add(2)?;
        }
    }
    None
}

fn find_webp_exif(bytes: &[u8]) -> Option<&[u8]> {
    if bytes.len() < 12 {
        return None;
    }
    if &bytes[0..4] != b"RIFF" || &bytes[8..12] != b"WEBP" {
        return None;
    }
    let mut offset = 12;
    while offset + 8 <= bytes.len() {
        let chunk_fourcc = &bytes[offset..offset + 4];
        let chunk_size = u32::from_le_bytes([
            bytes[offset + 4],
            bytes[offset + 5],
            bytes[offset + 6],
            bytes[offset + 7],
        ]) as usize;
        let start = offset + 8;
        let end = start.checked_add(chunk_size)?;
        if end > bytes.len() {
            break;
        }
        if chunk_fourcc == b"EXIF" {
            return Some(&bytes[start..end]);
        }
        let padded_size = if chunk_size % 2 == 1 { chunk_size + 1 } else { chunk_size };
        offset = offset.checked_add(8)?.checked_add(padded_size)?;
    }
    None
}

fn find_png_exif(bytes: &[u8]) -> Option<&[u8]> {
    if bytes.len() < 8 {
        return None;
    }
    let png_signature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
    if bytes[0..8] != png_signature {
        return None;
    }
    let mut offset = 8;
    while offset + 8 <= bytes.len() {
        let chunk_len = u32::from_be_bytes([
            bytes[offset],
            bytes[offset + 1],
            bytes[offset + 2],
            bytes[offset + 3],
        ]) as usize;
        let chunk_type = &bytes[offset + 4..offset + 8];
        let start = offset + 8;
        let end = start.checked_add(chunk_len)?;
        if end + 4 > bytes.len() {
            break;
        }
        if chunk_type == b"eXIf" {
            return Some(&bytes[start..end]);
        }
        offset = offset.checked_add(12)?.checked_add(chunk_len)?;
    }
    None
}

fn get_tiff_data(bytes: &[u8]) -> Option<&[u8]> {
    if bytes.len() >= 8 {
        let is_tiff = (bytes[0] == 0x49 && bytes[1] == 0x49 && bytes[2] == 0x2A && bytes[3] == 0x00)
            || (bytes[0] == 0x4D && bytes[1] == 0x4D && bytes[2] == 0x00 && bytes[3] == 0x2A);
        if is_tiff {
            return Some(bytes);
        }
    }
    if let Some(tiff) = find_exif_data(bytes) {
        return Some(tiff);
    }
    if let Some(tiff) = find_webp_exif(bytes) {
        return Some(tiff);
    }
    find_png_exif(bytes)
}

fn get_value_bytes<'a>(tiff: &'a [u8], entry: &[u8], endian: Endian) -> Option<&'a [u8]> {
    if entry.len() < 12 {
        return None;
    }
    let typ = endian.read_u16(entry, 2)?;
    let count = endian.read_u32(entry, 4)?;
    let type_size = match typ {
        1 | 2 | 7 => 1,
        3 => 2,
        4 | 9 => 4,
        5 | 10 => 8,
        _ => 1,
    };
    let total_size = (count as usize).checked_mul(type_size)?;
    if total_size <= 4 {
        Some(&entry[8..8 + total_size])
    } else {
        let val_offset = endian.read_u32(entry, 8)? as usize;
        let end = val_offset.checked_add(total_size)?;
        if end <= tiff.len() {
            Some(&tiff[val_offset..end])
        } else {
            None
        }
    }
}

fn read_ascii(bytes: &[u8]) -> Option<String> {
    let mut len = bytes.len();
    while len > 0 && (bytes[len - 1] == 0 || bytes[len - 1].is_ascii_whitespace()) {
        len -= 1;
    }
    let s = std::str::from_utf8(&bytes[..len]).ok()?;
    let cleaned: String = s.chars().filter(|c| c.is_ascii() && !c.is_ascii_control()).collect();
    if cleaned.is_empty() {
        None
    } else {
        Some(cleaned)
    }
}

fn read_short(bytes: &[u8], endian: Endian) -> Option<u16> {
    endian.read_u16(bytes, 0)
}

fn read_long(bytes: &[u8], endian: Endian) -> Option<u32> {
    endian.read_u32(bytes, 0)
}

fn read_rational(bytes: &[u8], endian: Endian) -> Option<(u32, u32)> {
    endian.read_rational(bytes, 0)
}

fn format_exposure_time(num: u32, den: u32) -> String {
    if den == 0 {
        return "".to_string();
    }
    if num == 0 {
        return "0".to_string();
    }
    if num >= den {
        let val = num as f64 / den as f64;
        if val.fract() < 0.01 {
            format!("{}", val.round() as u32)
        } else {
            format!("{:.1}", val)
        }
    } else {
        let val = den as f64 / num as f64;
        format!("1/{}", val.round() as u32)
    }
}

fn format_focal_length(num: u32, den: u32) -> String {
    if den == 0 {
        return "".to_string();
    }
    let val = num as f64 / den as f64;
    if val.fract() < 0.01 {
        format!("{} mm", val.round() as u32)
    } else {
        format!("{:.1} mm", val)
    }
}

fn parse_gps_coordinate(bytes: &[u8], ref_char: char, endian: Endian) -> Option<f64> {
    if bytes.len() < 24 {
        return None;
    }
    let d_num = endian.read_u32(bytes, 0)? as f64;
    let d_den = endian.read_u32(bytes, 4)? as f64;
    let m_num = endian.read_u32(bytes, 8)? as f64;
    let m_den = endian.read_u32(bytes, 12)? as f64;
    let s_num = endian.read_u32(bytes, 16)? as f64;
    let s_den = endian.read_u32(bytes, 20)? as f64;

    if d_den == 0.0 || m_den == 0.0 || s_den == 0.0 {
        return None;
    }

    let deg = d_num / d_den;
    let min = m_num / m_den;
    let sec = s_num / s_den;

    let mut val = deg + (min / 60.0) + (sec / 3600.0);
    if ref_char == 'S' || ref_char == 'W' {
        val = -val;
    }
    Some(val)
}

fn parse_exif(bytes: &[u8]) -> Option<ParsedMetadata> {
    let tiff = get_tiff_data(bytes)?;
    if tiff.len() < 8 {
        return None;
    }
    let endian = match &tiff[0..2] {
        b"II" => Endian::Little,
        b"MM" => Endian::Big,
        _ => return None,
    };
    let magic = endian.read_u16(tiff, 2)?;
    if magic != 42 {
        return None;
    }
    let first_ifd_offset = endian.read_u32(tiff, 4)? as usize;
    if first_ifd_offset == 0 || first_ifd_offset >= tiff.len() {
        return None;
    }

    let mut metadata = ParsedMetadata {
        make: None,
        model: None,
        focal_length: None,
        aperture: None,
        shutter_speed: None,
        iso: None,
        gps_latitude: None,
        gps_longitude: None,
        date_taken: None,
        orientation: 0,
    };

    let mut exif_ifd_offset: Option<u32> = None;
    let mut gps_ifd_offset: Option<u32> = None;

    let mut gps_lat_ref: Option<char> = None;
    let mut gps_lon_ref: Option<char> = None;
    let mut gps_lat_raw: Option<Vec<u8>> = None;
    let mut gps_lon_raw: Option<Vec<u8>> = None;

    let mut next_ifd = first_ifd_offset;
    while next_ifd != 0 && next_ifd < tiff.len() {
        let current_offset = next_ifd;
        let mut entries = Vec::new();
        let num_entries = endian.read_u16(tiff, current_offset).unwrap_or(0) as usize;
        let mut entry_offset = current_offset + 2;
        for _ in 0..num_entries {
            if entry_offset + 12 > tiff.len() {
                break;
            }
            entries.push(tiff[entry_offset..entry_offset + 12].to_vec());
            entry_offset += 12;
        }

        for entry in &entries {
            let tag = endian.read_u16(entry, 0).unwrap_or(0);
            match tag {
                0x010F => {
                    if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                        metadata.make = read_ascii(val_bytes);
                    }
                }
                0x0110 => {
                    if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                        metadata.model = read_ascii(val_bytes);
                    }
                }
                0x0132 => {
                    if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                        metadata.date_taken = read_ascii(val_bytes);
                    }
                }
                0x0112 => {
                    if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                        if let Some(o) = read_short(val_bytes, endian) {
                            metadata.orientation = o as i32;
                        }
                    }
                }
                0x8769 => {
                    if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                        exif_ifd_offset = read_long(val_bytes, endian);
                    }
                }
                0x8825 => {
                    if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                        gps_ifd_offset = read_long(val_bytes, endian);
                    }
                }
                _ => {}
            }
        }

        if entry_offset + 4 <= tiff.len() {
            next_ifd = endian.read_u32(tiff, entry_offset).unwrap_or(0) as usize;
        } else {
            break;
        }
    }

    if let Some(offset) = exif_ifd_offset {
        let offset = offset as usize;
        if offset < tiff.len() {
            let num_entries = endian.read_u16(tiff, offset).unwrap_or(0) as usize;
            let mut entry_offset = offset + 2;
            let mut entries = Vec::new();
            for _ in 0..num_entries {
                if entry_offset + 12 > tiff.len() {
                    break;
                }
                entries.push(tiff[entry_offset..entry_offset + 12].to_vec());
                entry_offset += 12;
            }

            for entry in &entries {
                let tag = endian.read_u16(entry, 0).unwrap_or(0);
                match tag {
                    0x829A => {
                        if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                            if let Some((num, den)) = read_rational(val_bytes, endian) {
                                metadata.shutter_speed = Some(format_exposure_time(num, den));
                            }
                        }
                    }
                    0x829D => {
                        if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                            if let Some((num, den)) = read_rational(val_bytes, endian) {
                                if den != 0 {
                                    let val = num as f64 / den as f64;
                                    metadata.aperture = Some(format!("{:.1}", val));
                                }
                            }
                        }
                    }
                    0x8827 => {
                        if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                            let typ = endian.read_u16(entry, 2).unwrap_or(0);
                            let iso_val = if typ == 3 {
                                read_short(val_bytes, endian).map(|v| v as u32)
                            } else {
                                read_long(val_bytes, endian)
                            };
                            if let Some(iso) = iso_val {
                                metadata.iso = Some(iso.to_string());
                            }
                        }
                    }
                    0x9003 | 0x9004 => {
                        if metadata.date_taken.is_none() {
                            if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                                metadata.date_taken = read_ascii(val_bytes);
                            }
                        }
                    }
                    0x920A => {
                        if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                            if let Some((num, den)) = read_rational(val_bytes, endian) {
                                metadata.focal_length = Some(format_focal_length(num, den));
                            }
                        }
                    }
                    _ => {}
                }
            }
        }
    }

    if let Some(offset) = gps_ifd_offset {
        let offset = offset as usize;
        if offset < tiff.len() {
            let num_entries = endian.read_u16(tiff, offset).unwrap_or(0) as usize;
            let mut entry_offset = offset + 2;
            let mut entries = Vec::new();
            for _ in 0..num_entries {
                if entry_offset + 12 > tiff.len() {
                    break;
                }
                entries.push(tiff[entry_offset..entry_offset + 12].to_vec());
                entry_offset += 12;
            }

            for entry in &entries {
                let tag = endian.read_u16(entry, 0).unwrap_or(0);
                match tag {
                    1 => {
                        if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                            if let Some(s) = read_ascii(val_bytes) {
                                gps_lat_ref = s.chars().next();
                            }
                        }
                    }
                    2 => {
                        if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                            gps_lat_raw = Some(val_bytes.to_vec());
                        }
                    }
                    3 => {
                        if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                            if let Some(s) = read_ascii(val_bytes) {
                                gps_lon_ref = s.chars().next();
                            }
                        }
                    }
                    4 => {
                        if let Some(val_bytes) = get_value_bytes(tiff, entry, endian) {
                            gps_lon_raw = Some(val_bytes.to_vec());
                        }
                    }
                    _ => {}
                }
            }
        }
    }

    if let (Some(lat_raw), Some(lat_ref)) = (gps_lat_raw, gps_lat_ref) {
        metadata.gps_latitude = parse_gps_coordinate(&lat_raw, lat_ref, endian);
    }
    if let (Some(lon_raw), Some(lon_ref)) = (gps_lon_raw, gps_lon_ref) {
        metadata.gps_longitude = parse_gps_coordinate(&lon_raw, lon_ref, endian);
    }

    Some(metadata)
}

fn escape_json_option_string(opt: &Option<String>) -> String {
    match opt {
        Some(s) => {
            let escaped = s.replace('\\', "\\\\").replace('"', "\\\"");
            format!("\"{}\"", escaped)
        }
        None => "null".to_string(),
    }
}

fn escape_json_option_double(opt: &Option<f64>) -> String {
    match opt {
        Some(v) => {
            if v.is_nan() || v.is_infinite() {
                "null".to_string()
            } else {
                v.to_string()
            }
        }
        None => "null".to_string(),
    }
}

// Global caching index
use std::sync::{Mutex, OnceLock};
use std::collections::HashMap;

fn get_cache() -> &'static Mutex<HashMap<String, String>> {
    static CACHE: OnceLock<Mutex<HashMap<String, String>>> = OnceLock::new();
    CACHE.get_or_init(|| Mutex::new(HashMap::new()))
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_getExifMetadata(
    mut env: JNIEnv,
    _class: JClass,
    key: jni::objects::JString,
    jpeg_bytes: jbyteArray,
) -> jni::sys::jstring {
    let key_str: String = match env.get_string(&key) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    // Check cache
    {
        let cache = get_cache().lock().unwrap();
        if let Some(cached_json) = cache.get(&key_str) {
            if let Ok(jstr) = env.new_string(cached_json) {
                return jstr.into_raw();
            }
        }
    }

    // Read bytes
    let array = match jni::objects::JByteArray::from_raw(jpeg_bytes) {
        arr => arr,
    };
    let len = env.get_array_length(&array).unwrap_or(0);
    if len <= 0 {
        return std::ptr::null_mut();
    }

    let mut buf = vec![0i8; len as usize];
    if env.get_byte_array_region(&array, 0, &mut buf).is_err() {
        return std::ptr::null_mut();
    }

    let u8_slice = std::slice::from_raw_parts(buf.as_ptr() as *const u8, len as usize);
    let metadata = match parse_exif(u8_slice) {
        Some(m) => m,
        None => return std::ptr::null_mut(),
    };

    let json = format!(
        r#"{{"make":{},"model":{},"focalLength":{},"aperture":{},"shutterSpeed":{},"iso":{},"gpsLatitude":{},"gpsLongitude":{},"dateTaken":{},"orientation":{}}}"#,
        escape_json_option_string(&metadata.make),
        escape_json_option_string(&metadata.model),
        escape_json_option_string(&metadata.focal_length),
        escape_json_option_string(&metadata.aperture),
        escape_json_option_string(&metadata.shutter_speed),
        escape_json_option_string(&metadata.iso),
        escape_json_option_double(&metadata.gps_latitude),
        escape_json_option_double(&metadata.gps_longitude),
        escape_json_option_string(&metadata.date_taken),
        metadata.orientation
    );

    // Save to cache
    {
        let mut cache = get_cache().lock().unwrap();
        cache.insert(key_str, json.clone());
    }

    match env.new_string(json) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_clearExifCache(
    mut env: JNIEnv,
    _class: JClass,
    key: jni::objects::JString,
) {
    let mut cache = get_cache().lock().unwrap();
    if key.as_raw().is_null() {
        cache.clear();
    } else {
        if let Ok(key_str) = env.get_string(&key) {
            let key_str: String = key_str.into();
            cache.remove(&key_str);
        }
    }
}

