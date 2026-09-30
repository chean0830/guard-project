/** 이용약관·개인정보처리방침 같은 긴 문서를 같은 모양으로 보여주는 틀. */
export type LegalSection = { title: string; body: (string | string[])[] }

export function LegalDocument({
  title,
  effectiveDate,
  intro,
  sections,
}: {
  title: string
  effectiveDate: string
  intro: string
  sections: LegalSection[]
}) {
  return (
    <article className="mx-auto w-full max-w-2xl px-4 py-12 sm:px-8">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">{title}</h1>
      <p className="mt-2 text-sm text-zinc-400">시행일: {effectiveDate}</p>
      <p className="mt-6 text-sm leading-relaxed text-zinc-600 dark:text-zinc-300">{intro}</p>

      {sections.map((section, index) => (
        <section key={section.title} className="mt-8">
          <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">
            제{index + 1}조 {section.title}
          </h2>
          <div className="mt-2 flex flex-col gap-2 text-sm leading-relaxed text-zinc-600 dark:text-zinc-300">
            {section.body.map((block, i) =>
              Array.isArray(block) ? (
                <ul key={i} className="list-disc pl-5">
                  {block.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : (
                <p key={i}>{block}</p>
              ),
            )}
          </div>
        </section>
      ))}
    </article>
  )
}
