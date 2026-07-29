# Generate Word/PDF with header.png, footer.png, and centered watermark
$ErrorActionPreference = 'Stop'
$mdPath = "C:\Abel_Project\SourceCode\gcomerce\ai-progress\INSA-Web-Application-Security-Audit-Documentation.md"
$docxPath = "C:\Abel_Project\SourceCode\gcomerce\ai-progress\INSA-Web-Application-Security-Audit-Documentation.docx"
$pdfPath = "C:\Abel_Project\SourceCode\gcomerce\ai-progress\INSA-Web-Application-Security-Audit-Documentation.pdf"
$basePath = "C:\Abel_Project\SourceCode\gcomerce\ai-progress"
$logoPath = "$basePath\logo.jpg"
$headerPath = "$basePath\header.png"
$footerPath = "$basePath\footer.png"

# Word constants
$wdAlignParagraphCenter = 1; $wdAlignParagraphLeft = 0; $wdAlignParagraphRight = 2
$wdStyleHeading1 = -2; $wdStyleHeading2 = -3; $wdStyleHeading3 = -4
$wdStyleNormal = -1; $wdStyleCaption = -35; $wdStory = 6
$wdFormatDocumentDefault = 16; $wdFormatPDF = 17
$wdSeekCurrentPageHeader = 9; $wdSeekCurrentPageFooter = 10
$wdSeekMainDocument = 0
# Shape positioning: relative to page (not margin)
# Word constants: Margin=0, Page=1, Column=2, Character=3
$wdRelativeHorizontalPositionPage = 1
$wdRelativeVerticalPositionPage = 1
# Wrap types (WdWrapType enum)
# 0=Square, 1=Tight, 2=Through, 3=None (floating), 4=TopBottom, 6=Inline
$wdWrapNone = 3    # Floating - free positioning
# ZOrder
$msoSendToBack = 1
$msoBringToFront = 2

# Colors (Word RGB format: R + G*256 + B*65536)
$colorBlack = 0

$word = New-Object -ComObject Word.Application
$word.Visible = $false; $word.DisplayAlerts = 0
try {
    if (Test-Path $docxPath) { Remove-Item $docxPath -Force }
    if (Test-Path $pdfPath) { Remove-Item $pdfPath -Force }
    $doc = $word.Documents.Add(); $selection = $word.Selection
    $doc.PageSetup.PaperSize = 7  # A4 (wdPaperA4=7, wdPaperA5=9)
    # Explicitly set page dimensions to ensure A4 size (21cm x 29.7cm)
    $doc.PageSetup.PageWidth = $word.CentimetersToPoints(21.0)
    $doc.PageSetup.PageHeight = $word.CentimetersToPoints(29.7)
    $doc.PageSetup.TopMargin = $word.CentimetersToPoints(5.0)    # Space for header image
    $doc.PageSetup.BottomMargin = $word.CentimetersToPoints(4.0) # Space for footer image
    $doc.PageSetup.LeftMargin = $word.CentimetersToPoints(2.5)
    $doc.PageSetup.RightMargin = $word.CentimetersToPoints(2.5)
    $doc.PageSetup.HeaderDistance = $word.CentimetersToPoints(0.5)  # Header close to top
    $doc.PageSetup.FooterDistance = $word.CentimetersToPoints(0.5)  # Footer close to bottom
    # Disable "Different First Page" so header/footer appear on ALL pages
    $doc.PageSetup.OddAndEvenPagesHeaderFooter = $false
    $doc.PageSetup.DifferentFirstPageHeaderFooter = $false
    $maxWidthCm = 16.0; $maxHeightCm = 21.0

    # ========== CREATE WATERMARK IMAGE ==========
    Add-Type -AssemblyName System.Drawing
    $origImg = [System.Drawing.Image]::FromFile($logoPath)
    $wmBmp = New-Object System.Drawing.Bitmap($origImg.Width, $origImg.Height)
    $wmBmp.SetResolution($origImg.HorizontalResolution, $origImg.VerticalResolution)
    $g = [System.Drawing.Graphics]::FromImage($wmBmp)
    $g.Clear([System.Drawing.Color]::White)
    
    $cm = New-Object System.Drawing.Imaging.ColorMatrix
    $cm.Matrix00 = 0.85; $cm.Matrix11 = 0.85; $cm.Matrix22 = 0.85
    $cm.Matrix33 = 0.20; $cm.Matrix44 = 1.0
    
    $ia = New-Object System.Drawing.Imaging.ImageAttributes
    $ia.SetColorMatrix($cm)
    $rect = New-Object System.Drawing.Rectangle(0, 0, $origImg.Width, $origImg.Height)
    $g.DrawImage($origImg, $rect, 0, 0, $origImg.Width, $origImg.Height, [System.Drawing.GraphicsUnit]::Pixel, $ia)
    $g.Dispose(); $ia.Dispose(); $origImg.Dispose()
    
    $wmLogoPath = "$basePath\watermark_logo.png"
    $wmBmp.Save($wmLogoPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $wmBmp.Dispose()
    Write-Output "Created watermark logo: $wmLogoPath"

    # ========== HEADER (using header.png as full-width floating shape) ==========
    $header = $doc.Sections.Item(1).Headers.Item(1)
    
    # header.png: 637x152 -> aspect ratio: at 21cm width, height = 21*152/637 = 5.01cm
    $hdrWidthCm = 21.0
    $hdrHeightCm = 21.0 * 152.0 / 637.0  # ~5.01cm
    
    # Add picture with explicit position (makes it a floating shape)
    $hdrShape = $header.Shapes.AddPicture(
        $headerPath,
        $false, $true,
        $word.CentimetersToPoints(0.0),                # Left
        $word.CentimetersToPoints(0.0),                # Top
        $word.CentimetersToPoints($hdrWidthCm),        # Width - 21cm
        $word.CentimetersToPoints($hdrHeightCm)        # Height - preserves aspect
    )
    # Set wrap and position relative to PAGE (must be done after AddPicture)
    $hdrShape.WrapFormat.Type = $wdWrapNone
    $hdrShape.RelativeHorizontalPosition = $wdRelativeHorizontalPositionPage
    $hdrShape.RelativeVerticalPosition = $wdRelativeVerticalPositionPage
    $hdrShape.Left = $word.CentimetersToPoints(0.0)
    $hdrShape.Top = $word.CentimetersToPoints(0.0)
    Write-Output ("Added header.png (full width: 21cm x {0}cm, at 0cm,0cm)" -f [math]::Round($hdrHeightCm, 2))

    # ========== WATERMARK (centered on page, behind text) ==========
    $wmWidthCm = 12.0
    $wmLeftCm = (21.0 - $wmWidthCm) / 2.0   # 4.5cm
    $wmTopCm = (29.7 - $wmWidthCm) / 2.0    # 8.85cm
    
    $wmShape = $header.Shapes.AddPicture(
        $wmLogoPath,
        $false, $true,
        $word.CentimetersToPoints($wmLeftCm),
        $word.CentimetersToPoints($wmTopCm),
        $word.CentimetersToPoints($wmWidthCm)
    )
    $wmShape.WrapFormat.Type = $wdWrapNone
    $wmShape.RelativeHorizontalPosition = $wdRelativeHorizontalPositionPage
    $wmShape.RelativeVerticalPosition = $wdRelativeVerticalPositionPage
    $wmShape.Left = $word.CentimetersToPoints($wmLeftCm)
    $wmShape.Top = $word.CentimetersToPoints($wmTopCm)
    $wmShape.ZOrder($msoSendToBack)
    Write-Output ("Added watermark (centered: left={0}cm, top={1}cm, width={2}cm)" -f $wmLeftCm, $wmTopCm, $wmWidthCm)

    # ========== FOOTER (using footer.png as full-width floating shape) ==========
    $footer = $doc.Sections.Item(1).Footers.Item(1)
    
    # footer.png: 637x113 -> at 21cm width, height = 21*113/637 = 3.73cm
    $ftrWidthCm = 21.0
    $ftrHeightCm = 21.0 * 113.0 / 637.0  # ~3.73cm
    $ftrTopCm = 29.7 - $ftrHeightCm      # ~25.97cm (bottom of page)
    
    $ftrShape = $footer.Shapes.AddPicture(
        $footerPath,
        $false, $true,
        $word.CentimetersToPoints(0.0),
        $word.CentimetersToPoints($ftrTopCm),
        $word.CentimetersToPoints($ftrWidthCm),
        $word.CentimetersToPoints($ftrHeightCm)
    )
    $ftrShape.WrapFormat.Type = $wdWrapNone
    $ftrShape.RelativeHorizontalPosition = $wdRelativeHorizontalPositionPage
    $ftrShape.RelativeVerticalPosition = $wdRelativeVerticalPositionPage
    $ftrShape.Left = $word.CentimetersToPoints(0.0)
    $ftrShape.Top = $word.CentimetersToPoints($ftrTopCm)
    Write-Output ("Added footer.png (full width: 21cm x {0}cm, at 0cm,{1}cm)" -f [math]::Round($ftrHeightCm, 2), [math]::Round($ftrTopCm, 2))

    $selection = $word.Selection

    # ========== DOCUMENT BODY ==========
    $lines = Get-Content $mdPath -Encoding UTF8
    $inCodeBlock = $false; $inTable = $false; $tableRows = @(); $imageCount = 0

    function Add-Image($sel, $p, $isLogo, $word, $maxW, $maxH) {
        if (Test-Path $p) {
            $sel.Style = $wdStyleNormal
            $sel.ParagraphFormat.Alignment = $wdAlignParagraphCenter
            $sh = $sel.InlineShapes.AddPicture($p, $false, $true)
            $sh.LockAspectRatio = $true
            if ($isLogo) {
                $sh.Width = $word.CentimetersToPoints(5)
            } else {
                $widthCm = $sh.Width / 28.35; $heightCm = $sh.Height / 28.35
                $ratio = [math]::Min($maxW / $widthCm, $maxH / $heightCm)
                if ($ratio -lt 1.0) { $sh.Width = $sh.Width * $ratio }
            }
            $sel.TypeParagraph(); $sel.ParagraphFormat.Alignment = $wdAlignParagraphLeft
        }
    }
    function Flush-Table($sel, [ref]$tr) {
        $rows = $tr.Value
        if ($rows.Count -ge 2) {
            $nc = ($rows[0] -split '\|' | Where-Object { $_.Trim() -ne '' }).Count
            $nr = $rows.Count
            $t = $sel.Tables.Add($sel.Range, $nr, $nc); $t.Borders.Enable = $true; $t.Style = "Table Grid"
            for ($r = 0; $r -lt $nr; $r++) {
                $cells = $rows[$r] -split '\|' | Where-Object { $_.Trim() -ne '' }
                for ($c = 0; $c -lt $cells.Count -and $c -lt $nc; $c++) {
                    $ct = $cells[$c].Trim() -replace '\*\*(.+?)\*\*', '$1' -replace '\*(.+?)\*', '$1' -replace '`(.+?)`', '$1'
                    $t.Cell($r + 1, $c + 1).Range.Text = $ct
                }
            }
            for ($c = 1; $c -le $nc; $c++) { $t.Cell(1, $c).Range.Bold = $true }
            $sel.EndKey($wdStory) | Out-Null; $sel.TypeParagraph()
        }
        $tr.Value = @()
    }

    $i = 0
    while ($i -lt $lines.Count) {
        $line = $lines[$i]; $i++
        if ($line -match '^```') {
            if ($inCodeBlock) { $inCodeBlock = $false; $selection.Style = $wdStyleNormal; continue }
            else { $inCodeBlock = $true; $selection.Style = $wdStyleNormal; $selection.Font.Name = "Consolas"; $selection.Font.Size = 9; continue }
        }
        if ($inCodeBlock) {
            $selection.Style = $wdStyleNormal; $selection.Font.Name = "Consolas"; $selection.Font.Size = 9
            $selection.TypeText($line); $selection.TypeParagraph()
            $selection.Font.Name = "Calibri"; $selection.Font.Size = 11; continue
        }
        if ($line -match '^\|.*\|$') {
            if ($line -match '^\|[\s\-:|]+\|$') { continue }
            $tableRows += $line; $inTable = $true; continue
        } elseif ($inTable) { Flush-Table $selection ([ref]$tableRows); $inTable = $false }
        if ($line -match '^!\[(.+?)\]\((.+?)\)') {
            $altText = $matches[1]; $rp = $matches[2] -replace '%20', ' '
            $ip = Join-Path $basePath $rp
            $isLogo = $altText -match "Logo"
            Add-Image $selection $ip $isLogo $word $maxWidthCm $maxHeightCm; $imageCount++; continue
        }
        if ($line -match '^\*(.+)\*$') {
            $selection.Style = $wdStyleCaption; $selection.ParagraphFormat.Alignment = $wdAlignParagraphCenter
            $selection.Range.Italic = $true; $selection.TypeText($matches[1]); $selection.Range.Italic = $false
            $selection.TypeParagraph(); $selection.ParagraphFormat.Alignment = $wdAlignParagraphLeft; continue
        }
        if ($line -match '^# (.+)$') { $selection.Style = $wdStyleHeading1; $selection.TypeText($matches[1]); $selection.TypeParagraph(); continue }
        if ($line -match '^## (.+)$') { $selection.Style = $wdStyleHeading2; $selection.TypeText($matches[1]); $selection.TypeParagraph(); continue }
        if ($line -match '^### (.+)$') { $selection.Style = $wdStyleHeading3; $selection.TypeText($matches[1]); $selection.TypeParagraph(); continue }
        if ($line -match '^---\s*$') { $selection.Style = $wdStyleNormal; $selection.TypeParagraph(); continue }
        if ($line -match '^\*\*(.+?):\*\*$') { $selection.Style = $wdStyleNormal; $selection.Font.Bold = $true; $selection.TypeText($matches[1] + ":"); $selection.Font.Bold = $false; $selection.TypeParagraph(); continue }
        if ($line.Trim() -ne '') {
            $selection.Style = $wdStyleNormal; $selection.Font.Name = "Calibri"; $selection.Font.Size = 11
            $segs = [regex]::Split($line, '(\*\*.+?\*\*|`.+?`|\[.+?\]\(.+?\))')
            foreach ($s in $segs) {
                if ($s -match '^\*\*(.+)\*\*$') { $selection.Font.Bold = $true; $selection.TypeText($matches[1]); $selection.Font.Bold = $false }
                elseif ($s -match '^`(.+)`$') { $selection.Font.Name = "Consolas"; $selection.Font.Size = 10; $selection.TypeText($matches[1]); $selection.Font.Name = "Calibri"; $selection.Font.Size = 11 }
                elseif ($s -match '^\[(.+?)\]\((.+?)\)$') { $selection.TypeText($matches[1]) }
                else { $selection.TypeText($s) }
            }
            $selection.TypeParagraph()
        } else { $selection.Style = $wdStyleNormal; $selection.TypeParagraph() }
    }
    if ($inTable) { Flush-Table $selection ([ref]$tableRows) }

    # Ensure all sections have the same header/footer (link to previous)
    for ($s = 2; $s -le $doc.Sections.Count; $s++) {
        $doc.Sections.Item($s).Headers.Item(1).LinkToPrevious = $true
        $doc.Sections.Item($s).Footers.Item(1).LinkToPrevious = $true
    }
    Write-Output "Linked all sections to first section header/footer ($($doc.Sections.Count) sections)"

    # Save DOCX
    Write-Output "Saving DOCX..."
    $doc.SaveAs([ref]$docxPath, [ref]$wdFormatDocumentDefault)
    # Export PDF
    Write-Output "Exporting PDF..."
    $doc.ExportAsFixedFormat($pdfPath, $wdFormatPDF)
    $doc.Close($false)
    Write-Output "SUCCESS: Both documents generated with $imageCount images + header + footer + watermark."
    Write-Output ("DOCX: " + [math]::Round((Get-Item $docxPath).Length/1024,1) + " KB")
    Write-Output ("PDF:  " + [math]::Round((Get-Item $pdfPath).Length/1024,1) + " KB")
} finally {
    $word.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
    [System.GC]::Collect(); [System.GC]::WaitForPendingFinalizers()
}
