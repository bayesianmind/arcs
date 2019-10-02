/**
 * @license
 * Copyright (c) 2017 Google Inc. All rights reserved.
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 * Code distributed by Google as part of this project is also
 * subject to an additional IP rights grant found at
 * http://polymer.github.io/PATENTS.txt
 */

import {assert} from '../../platform/assert-web.js';
import {Id} from '../id.js';
import {compareNumbers, compareStrings} from '../recipe/comparable.js';
import {Type} from '../type.js';
import {StorageStub} from '../storage-stub.js';
import {SerializedModelEntry} from './crdt-collection-model.js';
import {KeyBase} from './key-base.js';
import {Store, BigCollectionStore, CollectionStore, SingletonStore} from '../store.js';
import {PropagatedException} from '../arc-exceptions.js';
import {Dictionary, Consumer} from '../hot.js';
import {ClaimIsTag} from '../particle-claim.js';
import {UnifiedStore} from '../storageNG/unified-store.js';

// tslint:disable-next-line: no-any
type Callback = Consumer<Dictionary<any>>;

/**
 * Methods that must be implemented by a Singleton Storage Provider
 * that are not already defined in SingletonStore.
 */
export interface SingletonStorageProvider extends StorageProviderBase, SingletonStore {
  set(value: {}, originatorId?: string, barrier?: string): Promise<void>;
  clear(originatorId?: string, barrier?: string): Promise<void>;
}

/**
 * Methods that must be implemented by a Collection Storage Provider,
 * that are not already defined in CollectionStore.
 */
export interface CollectionStorageProvider extends StorageProviderBase, CollectionStore {
  // tslint:disable-next-line: no-any
  getMultiple(ids: string[]): Promise<any[]>;
  store(value, keys: string[], originatorId?: string): Promise<void>;
  storeMultiple(values: {}, keys: string[], originatorId?: string): Promise<void>;
  // tslint:disable-next-line: no-any
  removeMultiple(items: any[], originatorId?: string) : Promise<void>;
}

/**
 * Methods that must be implemented by a BigCollection Storage Provider,
 * that are not already defined in BigCollectionStore.
 */
export interface BigCollectionStorageProvider extends StorageProviderBase, BigCollectionStore {
  cursorVersion(cursorId: number);
  cloneFrom(store: StorageProviderBase | StorageStub);
  clearItemsForTesting(): void;
}

export abstract class StorageBase {
  protected _debug = false;

  protected constructor(protected readonly arcId: Id) {
    assert(arcId !== undefined, 'Arcs with storage must have ids');
  }

  abstract construct(id: string, type: Type, keyFragment: string) : Promise<StorageProviderBase>;
  abstract connect(id: string, type: Type, key: string) : Promise<StorageProviderBase>;
  abstract baseStorageKey(type: Type, key: string) : string;
  abstract baseStorageFor(type: Type, key: string) : Promise<StorageProviderBase>;
  abstract parseStringAsKey(s: string) : KeyBase;


  /**
   * Turn on debugginf for this storage provider.  Providers should
   * subclass this and react to changes in the debug value.
   */
  public set debug(d: boolean) {
    this._debug = d;
  }

  /**
   * Provides graceful shutdown for tests.
   */
  async shutdown(): Promise<void> {}
}

// tslint:disable-next-line: no-any
type DeltaItems = {value: any, keys?: string[], effective?: boolean}[];

export class ChangeEvent {
  public readonly add: DeltaItems;
  public readonly remove: DeltaItems;
  // tslint:disable-next-line: no-any
  public readonly data: any;
  public readonly version: number;
  public readonly originatorId: string;
  public readonly barrier: string;

  constructor(args: {add?: DeltaItems, remove?: DeltaItems, data?, version?: number, originatorId?: string, barrier?: string}) {
    Object.assign(this, args);
  }
}

/**
 * Docs TBD
 */
export abstract class StorageProviderBase extends UnifiedStore implements Store {
  protected unifiedStoreType: 'StorageProviderBase';

  private readonly listeners: Set<Callback> = new Set();
  private readonly _type: Type;

  protected readonly _storageKey: string;
  referenceMode = false;

  version: number|null;
  readonly id: string;
  originalId: string|null;
  name: string;
  source: string|null;
  description: string;
  /** Trust tags claimed by this data store. */
  claims: ClaimIsTag[];

  protected constructor(type: Type, name: string, id: string, key: string) {
    super();
    assert(id, 'id must be provided when constructing StorageProviders');
    assert(!type.hasUnresolvedVariable, 'Storage types must be concrete');
    this._type = type;
    this.name = name;
    this.version = 0;
    this.id = id;
    this.source = null;
    this._storageKey = key;
  }

  enableReferenceMode(): void {
    this.referenceMode = true;
  }

  get storageKey(): string {
    return this._storageKey;
  }

  get type(): Type {
    return this._type;
  }

  reportExceptionInHost(exception: PropagatedException) {
    // This class lives in the host, so it's safe to just rethrow the exception here.
    throw exception;
  }

  // TODO: add 'once' which returns a promise.
  on(callback: Callback): void {
    this.listeners.add(callback);
  }

  off(callback: Callback): void {
    this.listeners.delete(callback);
  }

  // TODO: rename to _fireAsync so it's clear that callers are not re-entrant.
  /**
   * Propagate updates to change listeners.
   */
  protected async _fire(details: ChangeEvent) {
    const callbacks = [...this.listeners];
    // Yield so that event firing is not re-entrant with mutation.
    await 0;
    for (const callback of callbacks) {
      callback(details);
    }
  }

  toString(handleTags?: string[]): string {
    const results: string[] = [];
    const handleStr: string[] = [];
    handleStr.push(`store`);
    if (this.name) {
      handleStr.push(`${this.name}`);
    }
    handleStr.push(`of ${this.type.toString()}`);
    if (this.id) {
      handleStr.push(`'${this.id}'`);
    }
    if (handleTags && handleTags.length) {
      handleStr.push(`${handleTags.join(' ')}`);
    }
    if (this.source) {
      handleStr.push(`in '${this.source}'`);
    }
    results.push(handleStr.join(' '));
    if (this.claims && this.claims.length > 0) {
      results.push(`  claim is ${this.claims.map(claim => claim.tag).join(' and is ')}`);
    }
    if (this.description) {
      results.push(`  description \`${this.description}\``);
    }
    return results.join('\n');
  }

  get apiChannelMappingId() {
    return this.id;
  }

  // TODO: make abstract?
  dispose() {}

  /**
   * @returns an object notation of this storage provider.
   */
  abstract async toLiteral(): Promise<{version: number, model: SerializedModelEntry[]}>;

  abstract cloneFrom(store: UnifiedStore | StorageStub): void;

  // TODO(shans): remove this when it's possible to.
  abstract async ensureBackingStore();

  // tslint:disable-next-line: no-any
  abstract backingStore: any;

  /**
   * Called by Particle Execution Host to synchronize it's proxy.
   */
  async modelForSynchronization(): Promise<{version: number, model: {}}> {
    return this.toLiteral();
  }
}
