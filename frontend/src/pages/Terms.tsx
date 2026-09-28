// Structure follows the client's reference (radianonline.co.zm/terms-and-conditions), rewritten
// for an optician. Facts (collection only, pay at shop, insurers) mirror the V50 WhatsApp FAQs —
// change both together.
const SECTIONS: [string, string[]][] = [
  ['General', [
    'This website, our WhatsApp service and our social media pages are run by Specskart Opticians & Eye Care ("Specskart", "we", "us"). By using them, or by placing an order or booking with us, you agree to these terms.',
    'We may update these terms at any time. Changes take effect when posted here (see the date above); continuing to use our services means you accept the latest version.',
  ]],
  ['Offers & promotions', [
    'Where an advert, post, message or page says "T&C apply", these terms apply to that offer.',
    'Offers are valid only for the period stated, or while stocks last where no period is stated, and may be changed or withdrawn at any time without notice.',
    'Unless stated otherwise, offers cannot be combined with any other offer, discount, promo code or insurance claim, are not exchangeable for cash, and are limited to one per customer.',
    'A free eye check-up is an eye test only. It does not include frames, lenses, contact lenses, medication or specialist referrals, and does not oblige you to buy anything.',
    'Prices shown in adverts are "from" prices. The final price of spectacles depends on your prescription, lens type, coatings and frame, and is confirmed by us before your order is made.',
  ]],
  ['Medical disclaimer', [
    'Information on this website, in our adverts and in WhatsApp chats is general information only. It is not medical advice and is not a substitute for an eye examination by a qualified optometrist.',
    'Our Frame Finder and virtual try-on are style guides only. They do not measure your eyes or your prescription.',
    'Lenses are made to the prescription you give us. Prescriptions from other practitioners should be recent; we may ask you to have your eyes re-tested before we make your lenses. If you have pain, sudden changes in vision, flashes or floaters, see an eye-care professional straight away.',
  ]],
  ['Orders & prices', [
    'Sending an order or booking through the website or WhatsApp is a request to buy. It is accepted only when we confirm it to you.',
    'All prices are in Zambian Kwacha (ZMW) and include VAT where applicable. We try to keep prices and product details accurate, but if we find an error we will contact you before your order is made, and you may go ahead at the correct price or cancel.',
    'Frames shown online are subject to availability. Colours on screen may differ slightly from the actual product.',
  ]],
  ['Payment', [
    'Orders are currently paid at our shop when you collect. Online payment is not yet available.',
    'We accept Prudential, Onelife and ZISC insurance, subject to your insurer approving the claim. Any amount your insurer does not cover is payable by you. NHIMA does not cover spectacles.',
  ]],
  ['Collection', [
    'Orders are collected from our main clinic. We do not currently deliver. If you arrange your own courier or send someone to collect for you, the order is your responsibility once it is handed over to them.',
    'Lens orders are made to measure; collection times we give are estimates and may be affected by things outside our control, such as supplier or lab delays.',
  ]],
  ['Returns, remakes & warranty', [
    'Please check your spectacles when you collect them. Lenses are made to your individual prescription, so they cannot be returned or exchanged for a change of mind.',
    'If there is a fault in manufacture, or the lenses do not match the prescription we worked from, contact us and we will check and correct it. This does not cover damage from accidents, misuse or normal wear, or a later change in your prescription.',
    'Nothing in these terms affects your rights under Zambian consumer protection law.',
  ]],
  ['WhatsApp & electronic communication', [
    'When you message us, fill in a form or order online, you agree that we may contact you on WhatsApp, SMS, phone or email about your enquiry, order, eye-test reminders and offers. You can ask us to stop marketing messages at any time by replying STOP or telling us on WhatsApp.',
    'How we handle your personal information is explained in our Privacy page.',
  ]],
  ['Liability', [
    'We provide this website and our online services "as is". We are not responsible for technical problems, errors or interruptions, or for losses caused by events outside our reasonable control.',
    'To the extent the law allows, our liability for any order is limited to the price you paid for it.',
  ]],
  ['Intellectual property', [
    'The content, images and logos on this website belong to Specskart or our suppliers and may not be copied or used for commercial purposes without our written permission.',
  ]],
  ['Governing law', [
    'These terms are governed by the laws of the Republic of Zambia, and any dispute will be dealt with by the courts in Lusaka. If any part of these terms is found invalid, the rest still applies.',
  ]],
]

export default function Terms() {
  return (
    <div className="container-x max-w-2xl py-16 text-ink/75">
      <p className="label">Terms & Conditions</p>
      <h1 className="mt-3 text-4xl text-ink">The small print.</h1>
      <p className="mt-3 text-sm text-ink/55">Last updated: 28 September 2026</p>
      {SECTIONS.map(([title, paras]) => (
        <section key={title} className="mt-10">
          <h2 className="text-lg text-ink">{title}</h2>
          <div className="mt-3 space-y-3">{paras.map(p => <p key={p}>{p}</p>)}</div>
        </section>
      ))}
    </div>
  )
}
