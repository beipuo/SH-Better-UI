// Author: dishanhai
// RGBA8 frames in the same row order. SSIM is averaged over 8×8 luminance windows.
export function pixelMetrics(a, b, width, height) {
  if (a.length !== b.length || a.length !== width * height * 4) throw new Error('Frame sizes differ');
  const luminance = (bytes, offset) => bytes[offset] * 0.2126 + bytes[offset + 1] * 0.7152 + bytes[offset + 2] * 0.0722;
  let absolute = 0, squared = 0, alpha = 0, changed = 0, edges = 0, edgeSamples = 0;
  let maxChannelError = 0, maxAlphaError = 0;
  for (let i = 0; i < a.length; i += 4) {
    const r = a[i] - b[i], g = a[i + 1] - b[i + 1], blue = a[i + 2] - b[i + 2];
    const opacity = Math.abs(a[i + 3] - b[i + 3]);
    const redError = Math.abs(r), greenError = Math.abs(g), blueError = Math.abs(blue);
    absolute += redError + greenError + blueError;
    squared += r * r + g * g + blue * blue;
    alpha += opacity;
    maxChannelError = Math.max(maxChannelError, redError, greenError, blueError);
    maxAlphaError = Math.max(maxAlphaError, opacity);
    if (r || g || blue || opacity) changed++;
    if ((i / 4 + 1) % width) {
      edges += Math.abs(Math.abs(luminance(a, i) - luminance(a, i + 4)) - Math.abs(luminance(b, i) - luminance(b, i + 4)));
      edgeSamples++;
    }
    if (i + width * 4 < a.length) {
      const next = i + width * 4;
      edges += Math.abs(Math.abs(luminance(a, i) - luminance(a, next)) - Math.abs(luminance(b, i) - luminance(b, next)));
      edgeSamples++;
    }
  }
  let ssim = 0, windows = 0;
  for (let top = 0; top < height; top += 8) {
    for (let left = 0; left < width; left += 8) {
      let totalA = 0, totalB = 0, totalAA = 0, totalBB = 0, totalAB = 0, count = 0;
      for (let y = top; y < Math.min(height, top + 8); y++) {
        for (let x = left; x < Math.min(width, left + 8); x++) {
          const offset = (y * width + x) * 4;
          const aY = luminance(a, offset), bY = luminance(b, offset);
          totalA += aY; totalB += bY; totalAA += aY * aY; totalBB += bY * bY; totalAB += aY * bY;
          count++;
        }
      }
      const meanA = totalA / count, meanB = totalB / count;
      const varianceA = Math.max(0, totalAA / count - meanA * meanA);
      const varianceB = Math.max(0, totalBB / count - meanB * meanB);
      const covariance = totalAB / count - meanA * meanB;
      const c1 = (0.01 * 255) ** 2, c2 = (0.03 * 255) ** 2;
      ssim += (2 * meanA * meanB + c1) * (2 * covariance + c2)
        / ((meanA * meanA + meanB * meanB + c1) * (varianceA + varianceB + c2));
      windows++;
    }
  }
  const pixels = width * height;
  return { changedPixels: changed, mae: absolute / (pixels * 3), rmse: Math.sqrt(squared / (pixels * 3)),
    ssim: ssim / windows, alphaMae: alpha / pixels, edgeMae: edgeSamples ? edges / edgeSamples : 0,
    maxChannelError, maxAlphaError };
}
