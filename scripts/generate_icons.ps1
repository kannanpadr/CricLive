Add-Type -AssemblyName System.Drawing

$sourcePath = 'C:\Users\kanna\.gemini\antigravity-ide\brain\9a3412c0-94b5-4602-9307-6dd74a16867c\.user_uploaded\media_1792691103232.jpg'
$srcImg = [System.Drawing.Image]::FromFile($sourcePath)

$resBase = 'D:\CricketLive\CricLive\android\app\src\main\res'

$sizes = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}

function Resize-Image($img, $width, $height) {
    $destRect = New-Object System.Drawing.Rectangle(0, 0, $width, $height)
    $destImg = New-Object System.Drawing.Bitmap($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $destImg.SetResolution($img.HorizontalResolution, $img.VerticalResolution)
    $g = [System.Drawing.Graphics]::FromImage($destImg)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $wrapMode = New-Object System.Drawing.Imaging.ImageAttributes
    $wrapMode.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $g.DrawImage($img, $destRect, 0, 0, $img.Width, $img.Height, [System.Drawing.GraphicsUnit]::Pixel, $wrapMode)
    $g.Dispose()
    return $destImg
}

function Resize-RoundImage($img, $size) {
    $destImg = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($destImg)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $size, $size)
    $g.SetClip($path)

    $destRect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $wrapMode = New-Object System.Drawing.Imaging.ImageAttributes
    $wrapMode.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $g.DrawImage($img, $destRect, 0, 0, $img.Width, $img.Height, [System.Drawing.GraphicsUnit]::Pixel, $wrapMode)
    $g.Dispose()
    return $destImg
}

foreach ($folder in $sizes.Keys) {
    $dir = Join-Path $resBase $folder
    if (-not (Test-Path $dir)) { 
        New-Item -ItemType Directory -Path $dir -Force | Out-Null 
    }
    $s = $sizes[$folder]
    
    # Square
    $sq = Resize-Image $srcImg $s $s
    $sqPath = Join-Path $dir 'ic_launcher.png'
    $sq.Save($sqPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $sq.Dispose()

    # Round
    $rd = Resize-RoundImage $srcImg $s
    $rdPath = Join-Path $dir 'ic_launcher_round.png'
    $rd.Save($rdPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $rd.Dispose()
    
    Write-Host "Generated $folder at size $s"
}

# Drawable 512x512
$drawDir = Join-Path $resBase 'drawable'
if (-not (Test-Path $drawDir)) { 
    New-Item -ItemType Directory -Path $drawDir -Force | Out-Null 
}
$big = Resize-Image $srcImg 512 512
$big.Save((Join-Path $drawDir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$big.Dispose()

# Also save high-res icon in public folder for web
$publicIcon = 'D:\CricketLive\CricLive\public\app-icon.png'
$webIcon = Resize-Image $srcImg 512 512
$webIcon.Save($publicIcon, [System.Drawing.Imaging.ImageFormat]::Png)
$webIcon.Dispose()

$srcImg.Dispose()
Write-Host "ALL ICONS GENERATED SUCCESSFULLY"
