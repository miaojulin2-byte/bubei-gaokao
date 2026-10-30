#!/bin/zsh
# 渲染实词词义画并放进 app：每个卡片 id 渲染 50 帧循环，压成动画 WebP，首帧另存 JPG 作静态图。
#   scripts/blender/build.sh gk-shi-078 gk-shi-087 ...
set -e
here=${0:A:h}
out=$here/../../app/src/main/assets/paintings
work=${TMPDIR:-/tmp}/bubei-render
blender=/Applications/Blender.app/Contents/MacOS/Blender
for id in "$@"; do
  rm -rf $work/$id && mkdir -p $work/$id
  $blender -b -P $here/render.py -- $id anim $work/$id > $work/$id.log 2>&1 || { tail -20 $work/$id.log; exit 1; }
  img2webp -loop 0 -lossy -q 72 -m 4 -d 40 $work/$id/f_*.png -o $out/$id.webp > /dev/null
  sips -s format jpeg -s formatOptions 82 $work/$id/f_0000.png --out $out/$id.jpg > /dev/null
  echo "$id $(du -h $out/$id.webp | cut -f1)"
done
