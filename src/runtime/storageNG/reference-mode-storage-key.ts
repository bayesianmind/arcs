/**
 * @license
 * Copyright (c) 2019 Google Inc. All rights reserved.
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 * Code distributed by Google as part of this project is also
 * subject to an additional IP rights grant found at
 * http://polymer.github.io/PATENTS.txt
 */

 import {StorageKey} from './storage-key.js';

export class ReferenceModeStorageKey extends StorageKey {
  constructor(public backingKey: StorageKey, public storageKey: StorageKey) {
    super('reference-mode');
  }

  embedKey(key: StorageKey) {
    return key.toString().replace(/\{/g, '{{').replace(/\}/g, '}}');
  }

  static unembedKey(key: string) {
    return key.replace(/\}\}/g, '}').replace(/\{\{/g, '}');
  }

  toString(): string {
    return `${this.protocol}://{${this.embedKey(this.backingKey)}}{${this.embedKey(this.storageKey)}}`;
  }

  childWithComponent(component: string): StorageKey {
    return new ReferenceModeStorageKey(this.backingKey, this.storageKey.childWithComponent(component));
  }

  static fromString(key: string, parse: (key: string) => StorageKey): ReferenceModeStorageKey {
    const match = key.match(/^reference-mode:\/\/{((?:\}\}|[^}])+)}{((?:\}\}|[^}])+)}$/);

    if (!match) {
      throw new Error(`Not a valid ReferenceModeStorageKey: ${key}.`);
    }
    const [_, backingKey, storageKey] = match;

    return new ReferenceModeStorageKey(
      parse(ReferenceModeStorageKey.unembedKey(backingKey)),
      parse(ReferenceModeStorageKey.unembedKey(storageKey))
    );
  }
}
