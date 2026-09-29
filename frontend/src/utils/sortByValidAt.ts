export type ValidAtRecord = { validAt: string }

/**
 * Return forecast records in chronological order without mutating the provider's array.
 * Invalid timestamps are kept at the end so callers can still surface or diagnose them.
 */
export function sortByValidAt<T extends ValidAtRecord>(records: readonly T[]): T[] {
  return [...records].sort((left, right) => {
    const leftTime = Date.parse(left.validAt)
    const rightTime = Date.parse(right.validAt)
    const leftValid = Number.isFinite(leftTime)
    const rightValid = Number.isFinite(rightTime)

    if (!leftValid && !rightValid) return 0
    if (!leftValid) return 1
    if (!rightValid) return -1
    return leftTime - rightTime
  })
}
