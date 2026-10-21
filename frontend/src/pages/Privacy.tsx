export default function Privacy() {
  return (
    <div className="container-x max-w-2xl py-16 text-ink/75">
      <p className="label">Privacy</p>
      <h1 className="mt-3 text-4xl text-ink">Your photo stays on your device.</h1>
      <div className="mt-8 space-y-4">
        <p>The Frame Finder analyses facial proportions to recommend frame styles. It does not identify you,
          and it is not biometric identity recognition.</p>
        <p><strong>The selfie is processed entirely in your browser.</strong> Only the resulting measurements
          (ratios and an estimated face shape) and your recommendations are stored, linked to your enquiry.</p>
        <p>By default the original image is never uploaded or retained. Your photo is not shared with advertising platforms.</p>
        <p>We record your consent for camera use and photo processing, with a timestamp and policy version. You can
          ask us to delete your data at any time on WhatsApp.</p>
      </div>
    </div>
  )
}
