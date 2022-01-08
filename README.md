## GlideHeifDecoder
[![](https://jitpack.io/v/msisuzney/GlideHeifDecoder.svg)](https://jitpack.io/#msisuzney/GlideHeifDecoder)
[![license](http://img.shields.io/badge/license-Apache2.0-brightgreen.svg?style=flat)](https://github.com/msisuzney/GlideHeifDecoder/blob/main/LICENSE)

GlideHeifDecoder is a [Glide](https://github.com/bumptech/glide) integration library for decoding and displaying still images in HEIC and AVIF formats on Android platforms.It is based on [libheif](https://github.com/strukturag/libheif),in which  makes use of libde265 for HEIC image decoding,dav1d for AVIF image decoding.

## What are HEIF,HEIC,AVIF
[HEIF](https://en.wikipedia.org/wiki/High_Efficiency_Image_File_Format) is a container format for storing individual images and image sequences.HEIC and AVIF are new image file formats employing HEVC (h.265) or AV1 image coding.They both use HEIF as the image container.
## Usage
Add it in your root build.gradle at the end of repositories:
```java
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```
Add the dependency.
```java
dependencies {
        implementation 'com.github.msisuzney:GlideHeifDecoder:0.5'
}
```
Add AppGlideModule to your source directory
file in application module，it will automatically load GlideHeifDecoder.
```java
import com.bumptech.glide.annotation.GlideModule;

@GlideModule
public class AppGlideModule extends com.bumptech.glide.module.AppGlideModule {
}

```

Now you can load images in HEIC and AVIF formats.
```java
Glide.with(this).load(/*images in HEIC and AVIF formats */).into(iv);
```

## Acknowledgement
* [libheif](https://github.com/strukturag/libheif)
* [libde265](https://github.com/strukturag/libde265)
* [dav1d](https://github.com/validvoid/dav1d)
* [GlideWebpDecoder](https://github.com/zjupure/GlideWebpDecoder)

## Lisense
The Library is distributed under [Apache-2.0-licensed](https://github.com/msisuzney/GlideHeifDecoder/blob/main/LICENSE).
